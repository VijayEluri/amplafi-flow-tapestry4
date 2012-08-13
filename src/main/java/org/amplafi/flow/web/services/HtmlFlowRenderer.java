package org.amplafi.flow.web.services;

import java.io.Writer;

import org.amplafi.flow.FlowConstants;
import org.amplafi.flow.FlowRenderer;
import org.amplafi.flow.FlowState;
import org.amplafi.flow.web.FlowResponse;
import org.amplafi.flow.web.FlowWebUtils;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.services.ResponseBuilder;

/**
 *
 * @author patmoore
 *
 */
public class HtmlFlowRenderer implements FlowRenderer {

	private IRequestCycle cycle;

	private ResponseBuilder responseBuilder;

	@Override
	public String getRenderResultType() {
		return FlowConstants.HTML;
	}

	public IRequestCycle getCycle() {
		return cycle;
	}

	public void setCycle(IRequestCycle cycle) {
		this.cycle = cycle;
	}

    public ResponseBuilder getResponseBuilder() {
        return responseBuilder;
    }

    public void setResponseBuilder(ResponseBuilder responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

	@Override
	public void render(FlowResponse flowResponse) {
		render(flowResponse.getFlowState());		
	}

	private void render(FlowState flowState) {
		FlowWebUtils.activateAndRenderPageIfNotNull(cycle, flowState != null ? flowState.getCurrentPage() : null, flowState, responseBuilder);
	}

	@Override
	public void describeFlow(Writer writer, String flowType) {
	}

	@Override
	public void render(Writer writer, FlowState flowState, String errorMessage,
			Exception exception) {
		render(flowState);
	}

}
