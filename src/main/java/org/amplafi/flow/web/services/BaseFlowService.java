/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */

package org.amplafi.flow.web.services;

import static org.amplafi.flow.FlowConstants.*;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.amplafi.flow.FlowConstants;
import org.amplafi.flow.FlowManager;
import org.amplafi.flow.FlowManagement;
import org.amplafi.flow.FlowState;
import org.amplafi.flow.FlowUtils;
import org.amplafi.flow.ServicesConstants;
import static org.amplafi.flow.launcher.FlowLauncher.*;
import org.amplafi.flow.validation.FlowValidationException;
import org.amplafi.flow.web.FlowResultHandler;
import org.amplafi.flow.web.FlowWebUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.PageRedirectException;
import org.apache.tapestry.services.LinkFactory;
import org.apache.tapestry.util.ContentType;
import org.apache.tapestry.web.WebResponse;


/**
 * Base implementation for Tapestry services that start / continue flows.
 *
 */
public abstract class BaseFlowService implements FlowService {
    public static final String JSON_DESCRIBE = "json/describe";
    private static final String SCRIPT_CONTENT_TYPE = "text/javascript";
    private LinkFactory linkFactory;
    private FlowManager flowManager;
    private WebResponse response;
    private HttpServletRequest httpServletRequest;
    private Log log;
    private String name;
    /**
     * if {@link org.amplafi.flow.launcher.FlowLauncher#COMPLETE_FLOW} is not supplied - this is the default value to use.
     */
    protected String defaultComplete;
    /**
     * the default way the results should be rendered. "html", "json", etc.
     */
    protected String renderResultDefault;
    protected boolean discardSessionOnExit;
    private FlowResultHandler resultHandler;
    private boolean assumeApiCall;

    @Override
    public void service(IRequestCycle cycle) throws IOException {
        String flowType = cycle.getParameter(ServicesConstants.FLOW_TYPE);
        String flowId = cycle.getParameter(FLOW_ID);
        String renderResult = cycle.getParameter(RENDER_RESULT);

        if (JSON_DESCRIBE.equals(renderResult)) {
            describeService(cycle, flowType);
            return;
        }

        Map<String, String> initial = FlowUtils.INSTANCE.createState(FlowConstants.FSAPI_CALL, isAssumeApiCall());
        // TODO map cookie to the json flow state.
        String cookieString = cycle.getParameter(ServicesConstants.COOKIE_OBJECT);
        if(StringUtils.isNotBlank(cookieString)){
            initial.put(ServicesConstants.COOKIE_OBJECT, cookieString);
        }
        // HACK needed until https://issues.apache.org/jira/browse/TAPESTRY-1876
        // is addressed.
        String[] keyList = cycle.getParameters(_KEY_LIST);
        if ( keyList != null && keyList.length > 0) {
            for(String key: keyList) {
                String value = cycle.getParameter(key);
                if ( value != null ) {
                    // we do allow the value to be blank ( value may be existence of parameter)
                    initial.put(key, value);
                }
            }
        }

        String referingUriStr = cycle.getInfrastructure().getRequest().getHeader("Referer");
        if(StringUtils.isNotBlank(referingUriStr)){
            URI referingUri;
            try {
                referingUri = new URI(referingUriStr);
                initial.put(FSREFERING_URL, referingUri.toString());
            } catch (URISyntaxException e) {
                // ignore bad uri
            }
        }
        String complete = cycle.getParameter(COMPLETE_FLOW);

        doActualService(cycle, flowType, flowId, renderResult, initial, complete);
    }
    // TODO look at eliminating passing of cycle so that calls will be less tapestry specific.
    public abstract FlowState doActualService(IRequestCycle cycle, String flowType,
        String flowId, String resultsRenderedAs, Map<String, String> initial, String complete) throws IOException;

    /**
     * Render a json description of the flow. This includes parameters (name, type, required).
     * @param cycle
     * @param flowType
     * @throws IOException
     */
    public abstract void describeService(IRequestCycle cycle, String flowType) throws IOException;

    // TODO look at eliminating passing of cycle so that calls will be less tapestry specific.
    protected FlowState getFlowState(String flowType, String flowId, String renderResult, Map<String, String> initial, Writer writer, boolean currentFlow) throws IOException {
        FlowState flowState = null;
        if ( isNotBlank(flowId)) {
            flowState = getFlowManagement().getFlowState(flowId);
        }
        if ( flowState == null && isNotBlank(flowType)) {

            if(!getFlowManager().isFlowDefined(flowType)) {
                renderError(writer, flowType+": no such flow type", renderResult, null);
                return null;
            }

            String returnToFlowLookupKey = null;
            flowState = getFlowManagement().startFlowState(flowType, currentFlow, initial, returnToFlowLookupKey );
            if ( flowState == null ) {
                renderError(writer, flowType+": could not start flow type", renderResult, null);
                return null;
            }
        } else {
            renderError(writer, "neither "+ServicesConstants.FLOW_TYPE+" nor "+FLOW_ID+" in parameters", renderResult, null);
            return null;
        }
        return flowState;
    }

    protected abstract void renderError(Writer writer, String message, String renderResult, FlowState flowState) throws IOException;

    public void setLinkFactory(LinkFactory linkFactory) {
        this.linkFactory = linkFactory;
    }

    public LinkFactory getLinkFactory() {
        return linkFactory;
    }

    public void setFlowManager(FlowManager flowManager) {
        this.flowManager = flowManager;
    }

    public FlowManager getFlowManager() {
        return flowManager;
    }

    public FlowManagement getFlowManagement() {
        return getFlowManager().getFlowManagement();
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public Log getLog() {
        return log;
    }

    public void setResponse(WebResponse response) {
        this.response = response;
    }

    public WebResponse getResponse() {
        return response;
    }

    public void setHttpServletRequest(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    public HttpServletRequest getHttpServletRequest() {
        return httpServletRequest;
    }

    protected String advanceFlow(FlowState flowState) {
        String error = "some error occured";
        if ( flowState.isCurrentActivityCompletable()) {
            try {
                flowState.next();
                error = null;
            } catch(FlowValidationException flowValidationException) {
                getLog().debug(flowState.getLookupKey(), flowValidationException);
            } catch(Exception e) {
                // TODO attach exception to output somehow.
                getLog().error(flowState.getLookupKey(), e);
                error = flowState.getLookupKey()+" "+e.getMessage()+join(e.getStackTrace(), ", ");
            }
        }
        return error;
    }

    public void setDefaultComplete(String defaultComplete) {
        this.defaultComplete = defaultComplete;
    }

    public String getDefaultComplete() {
        return defaultComplete;
    }

    public void setRenderResultDefault(String renderResultDefault) {
        this.renderResultDefault = renderResultDefault;
    }

    public String getRenderResultDefault() {
        return renderResultDefault;
    }

    public void setDiscardSessionOnExit(boolean discardSession) {
        this.discardSessionOnExit = discardSession;
    }

    public boolean isDiscardSessionOnExit() {
        return discardSessionOnExit;
    }

    // TODO look at eliminating passing of cycle so that calls will be less tapestry specific.
    protected PrintWriter getWriter(IRequestCycle cycle) throws IOException {
        ContentType contentType = new ContentType(SCRIPT_CONTENT_TYPE);

        String encoding = contentType.getParameter("charset");

        try {
            if (encoding == null) {
                encoding = cycle.getEngine().getOutputEncoding();
                contentType.setParameter("charset", encoding);
            }
            return getResponse().getPrintWriter(contentType);
        } catch (NullPointerException nullPointerException) {
            // can happen if the cycle is not available (called in a headless/ non-tapestry way. )
            return null;
        }
    }

    // TODO -- this should not be necessary any more.
    public void continueFlowState(String flowLookupKey, Map<String, String> propertyChanges) throws PageRedirectException {
        FlowState flowState = getFlowManagement().continueFlowState(flowLookupKey, true, propertyChanges);
        if (flowState != null) {
            throw new PageRedirectException(flowState.getCurrentPage());
        }
    }

    // TODO look at eliminating passing of cycle so that calls will be less tapestry specific.
    protected void renderValidationException(FlowValidationException e, String flowType, Writer writer) throws IOException {
        writer.append("Cannot start ").append(flowType).append(" :\n");
        writer.append(e.getTrackings().toString());

        writer.append(e.toString());
    }

    public FlowResultHandler getResultHandler() {
        return resultHandler;
    }

    public void setResultHandler(FlowResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    /**
     * @param assumeApiCall the assumeApiCall to set
     */
    public void setAssumeApiCall(boolean assumeApiCall) {
        this.assumeApiCall = assumeApiCall;
    }

    /**
     * @return the assumeApiCall
     */
    public boolean isAssumeApiCall() {
        return assumeApiCall;
    }

    protected void renderHtml(FlowState flowState) {
        String page = flowState.getCurrentPage();
        // page should always be not null - if that's not the case, then
        // check the pageName attribute of flow definitions in the xml files
        if (page == null) {
            throw new IllegalStateException("pageName not defined for flow " + flowState.getFlowTypeName());
        }
        FlowWebUtils.activatePageIfNotNull(null, page, flowState);
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
