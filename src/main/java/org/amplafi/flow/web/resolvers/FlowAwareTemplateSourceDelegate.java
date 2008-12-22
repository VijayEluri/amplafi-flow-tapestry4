package org.amplafi.flow.web.resolvers;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sf.tacos.markup.ExtendedMarkupWriterImpl;
import net.sf.tacos.markup.IExtendedMarkupWriter;

import org.amplafi.flow.Flow;
import org.amplafi.flow.FlowActivity;
import org.amplafi.flow.FlowConstants;
import org.amplafi.flow.FlowDefinitionsManager;
import org.amplafi.flow.flowproperty.FlowPropertyDefinition;
import org.amplafi.flow.web.FlowWebUtils;
import org.amplafi.flow.web.components.FullFlowComponent;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.hivemind.ApplicationRuntimeException;
import org.apache.hivemind.Location;
import org.apache.hivemind.Resource;
import org.apache.tapestry.IComponent;
import org.apache.tapestry.IMarkupWriter;
import org.apache.tapestry.INamespace;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.annotations.AnnotationUtils;
import org.apache.tapestry.annotations.Parameter;
import org.apache.tapestry.engine.ITemplateSourceDelegate;
import org.apache.tapestry.markup.MarkupWriterImpl;
import org.apache.tapestry.markup.UTFMarkupFilter;
import org.apache.tapestry.parse.ComponentTemplate;
import org.apache.tapestry.parse.ITemplateParser;
import org.apache.tapestry.parse.ITemplateParserDelegate;
import org.apache.tapestry.parse.TemplateParseException;
import org.apache.tapestry.parse.TemplateToken;
import org.apache.tapestry.resolver.ComponentSpecificationResolver;
import org.apache.tapestry.services.impl.DefaultParserDelegate;
import org.apache.tapestry.spec.IComponentSpecification;


import static org.amplafi.flow.FlowConstants.*;
import static org.apache.commons.lang.StringUtils.*;


/**
 * Creates the template for a {@link FullFlowComponent flow}.
 * <p/>
 * This is done by iterating over all {@link FlowActivity activities}
 * of the given flow, and building a template that contains the
 * component defined in each activity.
 * <p/>
 * The parameters of those components are bound using the
 * {@link FlowPropertyDefinition definitions} in each
 * {@link FlowActivity activity}.
 * <p/>
 * Additionally, if the components contain required parameters (currently
 * those are detected only if annotations are used) that have not been
 * bound, they are bound to properties (of the activity or the flow)
 * having the same name. This allows creating activities without
 * specifying property definitions - but you'll usually need to do that
 * in order to customize the definition and get the data that those
 * properties hold.
 *
 * @see org.amplafi.flow.web.components.FullFlowComponent
 */
public class FlowAwareTemplateSourceDelegate implements ITemplateSourceDelegate {

    /**
     *
     */
    public static final String VISIBLE_FLOW_IF = "visibleFlowIf";
    /**
     *
     */
    private static final String OGNL = "ognl:";
    /**
     *
     */
    private static final String ADDITIONAL_UPDATE_COMPONENTS = "additionalUpdateComponents";
    /**
     *
     */
    private static final String DEBUG = "debug";
    /**
     *
     */
    private static final String ASYNC = "async";
    /**
     *
     */
    private static final String PREVIOUS_LISTENER = "previousListener";
    /**
     *
     */
    private static final String NEXT_LISTENER = "nextListener";
    /**
     *
     */
    private static final String FINISH_LISTENER = "finishListener";
    /**
     *
     */
    private static final String CANCEL_LISTENER = "cancelListener";
    /**
     *
     */
    private static final String END_LISTENER = "endListener";
    /**
     *
     */
    private static final String UPDATE_COMPONENTS = "updateComponents";
    /**
     *
     */
    private static final String HIDE_FLOW_CONTROL = "hideFlowControl";
    /**
     *
     */
    public static final String FLOW_BORDER_COMPONENT = "flow:FlowBorder";
    static final String FLOW_TO_USE = OGNL + "flowToUse";
    static final String ATTACH_OGNL = " " + FlowConstants.ATTACHED_FLOW + "=\"" + FLOW_TO_USE + "\" ";
    private static final String JWCID = "jwcid";
    private static final String[] FIXED_PARAMETERS = { ASYNC, UPDATE_COMPONENTS };
    private ITemplateParser parser;
    private ComponentSpecificationResolver componentSpecificationResolver;
    private FlowDefinitionsManager flowDefinitionsManager;
    private Log log;
    private String debugCondition;
    private String additionalUpdateComponents;

    public void setParser(ITemplateParser parser) {
        this.parser = parser;
    }

    public void setComponentSpecificationResolver(ComponentSpecificationResolver componentSpecificationResolver) {
        this.componentSpecificationResolver = componentSpecificationResolver;
    }

    public void setFlowDefinitionsManager(FlowDefinitionsManager flowDefinitionsManager) {
        this.flowDefinitionsManager = flowDefinitionsManager;
    }

    public void setDebugCondition(String debugCondition) {
        this.debugCondition = debugCondition;
    }

    public void setAdditionalUpdateComponents(String additionalUpdateComponents) {
        this.additionalUpdateComponents = additionalUpdateComponents;
    }

    public ComponentTemplate findTemplate(IRequestCycle cycle, IComponent component, Locale locale) {
        ComponentTemplate ret = null;
        IComponentSpecification spec = component.getSpecification();
        if (spec.getComponentClassName().equals(FullFlowComponent.class.getName())) {
            String type = spec.getDescription();
            Flow flow = flowDefinitionsManager.getFlowDefinition(type);
            // build the content for this full flow component
            String content;
            if (flow == null) {
                content = "<div>[Flow " + type + " not found]</div>";
            } else if ( CollectionUtils.isEmpty(flow.getActivities())) {
                content = "<div>[Flow " + type + " has no activites]</div>";
            } else {
                content = createTemplate(flow, cycle, component.getNamespace(), component.getLocation());
            }
            // now that we have the content, enhance the location assigned to the spec
            spec.setLocation(new MemoryMappedLocation(spec.getLocation(), content));
            // finally, create the template
            ret = constructTemplateInstance(cycle, content.toCharArray(),
                    spec.getSpecificationLocation(), component);
        }
        return ret;
    }

    private String createTemplate(Flow flow, IRequestCycle cycle, INamespace containerNamespace, Location location) {
        String flowName = flow.getFlowTypeName();
        StringWriter sw = new StringWriter();
        IExtendedMarkupWriter writer = new ExtendedMarkupWriterImpl(createMarkupWriter(new PrintWriter(sw)));
        //writer.create("span", JWCID, "allFlow@If", "renderTag", "false", "condition", "ognl:visibleFlow").println();
        // 10/15/2007 - TODO Tap bug #? -- there is an issue with the cancel listener being
        // discarded if the flow component is in an external form.
        writer.create("span", JWCID, "inF@If", "renderTag", "false", "condition", OGNL+"insideForm").println();
        writer.createEmpty("span", JWCID, "@RenderBlock", "block", "component:flowBlock");
        writer.end();

        writer.create("span", JWCID, "orF@Else", "renderTag", "false").println();

        // TODO 2007/10/15 -- we should make this stateless form. ... need to embed current flowstate into form.
        writer.create("form", JWCID, flowName + "FlowForm@Form",
                ASYNC, OGNL+ASYNC, "clientValidationEnabled", "true",
                "class", OGNL +"className", "delegate", OGNL + "delegate",
                "cancel", "listener:doCancelForm",
                "refresh", "listener:doRefreshForm",
                "stateful", OGNL +"stateful"
        );
        writer.createEmpty("span", JWCID, "@RenderBlock", "block", "component:flowBlock");
        writer.createEmpty("span", JWCID, "@flow:AttachFlowValidators", "validators", "validators:flow");
        writer.end();

        writer.end();
        writer.println();

        writer.create("span", JWCID, "flowBlock@Block").println();
        writer.create("div", JWCID, VISIBLE_FLOW_IF +
                "@If", "condition", OGNL+"visibleFlow", "renderTag", "false").println();
        int counter = 0;
        for (FlowActivity activity: flow.getActivities()) {
            String componentName = activity.getComponentName();
            if ( StringUtils.isBlank(componentName)) {
                continue;
            }
            IComponentSpecification specification;
            try {
                componentSpecificationResolver.resolve(cycle, containerNamespace, componentName, location);
                specification = componentSpecificationResolver.getSpecification();
            } catch (ApplicationRuntimeException e) {
                // couldn't find the component :-( ... normal for invisible components.
                activity.setInvisible(true);
                continue;
            }
            String blockName = FlowWebUtils.getBlockName(activity.getIndex());
            writer.create("div", JWCID, blockName + "@Block").println();
            String flowComponentName = FlowWebUtils.getFlowComponentName(counter);
            writer.createEmpty("div", JWCID, flowComponentName + "@" + componentName);

            HashSet<String> matchedParameters = new HashSet<String>();
            assignFlowParameters(flow, writer, activity, specification, flowComponentName, matchedParameters);
            writer.end();
            writer.println();
            counter++;
        }

        writer.create("div",
                JWCID, FullFlowComponent.FLOW_BORDER_COMPONENT_NAME+"@"+FLOW_BORDER_COMPONENT,
                HIDE_FLOW_CONTROL, OGNL+HIDE_FLOW_CONTROL,
                UPDATE_COMPONENTS, OGNL+UPDATE_COMPONENTS,
                END_LISTENER, OGNL+END_LISTENER,
                CANCEL_LISTENER, OGNL+CANCEL_LISTENER,
                FINISH_LISTENER, OGNL+FINISH_LISTENER,
                ASYNC, OGNL+ASYNC,
                NEXT_LISTENER, OGNL+NEXT_LISTENER,
                PREVIOUS_LISTENER, OGNL+PREVIOUS_LISTENER
        );
        if (debugCondition!=null) {
            writer.attribute(DEBUG, debugCondition);
        }
        if (additionalUpdateComponents!=null) {
            writer.attribute(ADDITIONAL_UPDATE_COMPONENTS, additionalUpdateComponents);
        }
        Set<String> matchedParameters = new HashSet<String>(Arrays.asList(HIDE_FLOW_CONTROL,
                UPDATE_COMPONENTS, END_LISTENER, CANCEL_LISTENER, FINISH_LISTENER, NEXT_LISTENER, PREVIOUS_LISTENER, ASYNC,
                DEBUG, ADDITIONAL_UPDATE_COMPONENTS, "usingLinkSubmit", "disabled"));

        // TODO can we figure out a way to have the IComponentSpecification for FlowBorder to be cached?
        componentSpecificationResolver.resolve(cycle, containerNamespace, FLOW_BORDER_COMPONENT, location);
        IComponentSpecification flowBorderSpecification = componentSpecificationResolver.getSpecification();
        assignFlowParameters(flow, writer, null, flowBorderSpecification, FullFlowComponent.FLOW_BORDER_COMPONENT_NAME, matchedParameters);

        writer.createEmpty("span", JWCID, "@RenderBody").println();
        writer.createEmpty("div", JWCID, "@RenderBlock", "block", OGNL +"currentBlock").println();
        writer.end();
        writer.end();
        writer.println();
        // close the @Block now
        writer.end();
        writer.println();
        // close the if visible
        //writer.end();
        //writer.println();

        String content = sw.toString();
        if ( getLog().isDebugEnabled()) {
            getLog().debug("generated template for Flow '" + flowName + "':");
            getLog().debug(content);
        }
        return content;
    }

    /**
     * Connects a component's parameters to the appropriate flow property.
     * If a {@link FlowActivity} has a property that is specific to that FlowActivity, that property will
     * be used rather than the {@link Flow}-level defined property.
     *
     * Additionally, {@link FlowConstants#ATTACHED_FLOW} parameter (if present and not already assigned)
     * will be connected to the FullFlowComponent's {@link #FLOW_TO_USE} method.
     * @param flow
     * @param writer
     * @param activity
     * @param componentName
     * @param matchedParameters
     */
    private void assignFlowParameters(Flow flow, IExtendedMarkupWriter writer, FlowActivity activity, IComponentSpecification specification, String componentName,
            Set<String> matchedParameters) {

        // add components required parameters
        Map<String, Parameter> foundParameters = findParameters(specification);

        // add flow properties that are not masked by activity-specific property definitions
        if ( flow.getPropertyDefinitions() != null ) {
            for(FlowPropertyDefinition definition: flow.getPropertyDefinitions().values()) {
                String name = definition.getName();
                if ( activity == null || activity.getPropertyDefinition(name) == definition) {
                    writeConnection(writer, componentName, matchedParameters, foundParameters, name, definition.getParameterName());
                }
            }
        }

        // add the FlowActivity-specific properties
        if (activity != null && activity.getPropertyDefinitions() != null ) {
            for(FlowPropertyDefinition definition: activity.getPropertyDefinitions().values()) {
                writeConnection(writer, componentName, matchedParameters, foundParameters, definition.getName(), definition.getParameterName());
            }
        }

        // now connect parameters that almost certainly should always be directly connected to the surrounding FullFlowComponent.
        // now make the attached flow available
        // TODO check to see if component allow informal parameters if
        // attachedFlow is not on the parameter list.
        if (foundParameters.containsKey(FlowConstants.ATTACHED_FLOW)  && !matchedParameters.contains(FlowConstants.ATTACHED_FLOW)) {
            writer.attribute(FlowConstants.ATTACHED_FLOW, FLOW_TO_USE);
            matchedParameters.add(FlowConstants.ATTACHED_FLOW);
        }
        for (String parameter: FIXED_PARAMETERS) {
            if (foundParameters.containsKey(parameter)  && !matchedParameters.contains(parameter)) {
                writer.attribute(parameter, OGNL+parameter);
                matchedParameters.add(parameter);
            }
        }
        // now add missing required parameters
        for(Map.Entry<String, Parameter> entry: foundParameters.entrySet()) {
            String parameter = entry.getKey();
            writeConnection(writer, componentName, matchedParameters, foundParameters, parameter, parameter);
        }
    }

    /**
     * @param writer
     * @param componentName TODO
     * @param matchedParameters
     * @param foundParameters
     */
    private void writeConnection(IExtendedMarkupWriter writer, String componentName, Set<String> matchedParameters,
            Map<String, Parameter> foundParameters, String name, String parameterName) {
        if (foundParameters.containsKey(name)  && !matchedParameters.contains(name)) {
            String value = FLOW_PROPERTY_PREFIX + name;
            String defaultValue = foundParameters.get(name).defaultValue();
            if( isBlank(defaultValue)) {
                writer.attribute(parameterName, value);
            } else {
                // non-blank defaultValue. Need to supply the flowComponent name so that FlowPropertyBinding can have the correct context to work from.
                writer.attribute(parameterName, value+"="+componentName+"@"+defaultValue);
            }
            matchedParameters.add(parameterName);
        }
    }

    public IMarkupWriter createMarkupWriter(PrintWriter printWriter) {
        return new MarkupWriterImpl("text/html", printWriter, new UTFMarkupFilter());
    }

    private synchronized ComponentTemplate constructTemplateInstance(IRequestCycle cycle, char[] templateData,
            Resource resource, IComponent component) {
        ITemplateParserDelegate delegate = new DefaultParserDelegate(component, JWCID,
                cycle, componentSpecificationResolver);

        TemplateToken[] tokens;
        try {
            tokens = parser.parse(templateData, delegate, resource);
        } catch (TemplateParseException ex) {
            throw new ApplicationRuntimeException("unableToParseTemplate " + resource, ex);
        }
        return new ComponentTemplate(templateData, tokens);
    }

    /**
     * We have to do this by hand because at this point in the code,
     * {@link IComponentSpecification#getRequiredParameters()} has no
     * value.
     * @param spec The specification of the component whose class we're interested
     * in investigating for required params.
     * @return list of required parameter names.
     */
    private Map<String, Parameter> findParameters(IComponentSpecification spec) {
        Map<String, Parameter> list = new LinkedHashMap<String, Parameter>();
        try {
            Class<?> clazz = Class.forName(spec.getComponentClassName());
            for (Method method : clazz.getMethods()) {
                Parameter parameter = method.getAnnotation(Parameter.class);
                if (parameter!=null) {
                    String name = parameter.name();
                    if (StringUtils.isBlank(name)) {
                        name = AnnotationUtils.getPropertyName(method);
                    }
                    list.put(name, parameter);
                }
            }
        } catch (ClassNotFoundException e) {
            getLog().error("while finding required parameters", e);
        }
        return list;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public Log getLog() {
        return log;
    }

}
