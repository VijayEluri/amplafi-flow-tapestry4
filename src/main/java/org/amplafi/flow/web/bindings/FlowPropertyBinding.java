/*
 * Created on Jun 3, 2007 Copyright 2006 by Patrick Moore
 */
package org.amplafi.flow.web.bindings;

import org.amplafi.flow.FlowActivity;
import org.amplafi.flow.FlowManagement;
import org.amplafi.flow.FlowState;
import org.amplafi.flow.flowproperty.FlowPropertyDefinition;
import org.amplafi.flow.web.FlowProvider;
import org.apache.hivemind.Location;
import org.apache.hivemind.util.Defense;
import org.apache.tapestry.BindingException;
import org.apache.tapestry.IBinding;
import org.apache.tapestry.IComponent;
import org.apache.tapestry.IRender;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.binding.BindingConstants;
import org.apache.tapestry.binding.BindingFactory;
import org.apache.tapestry.binding.BindingSource;
import org.apache.tapestry.coerce.ValueConverter;
import org.apache.tapestry.form.AbstractFormComponent;
import org.apache.tapestry.form.ValidatableField;
import org.apache.tapestry.valid.ValidatorException;


import static org.apache.commons.lang.StringUtils.*;

/**
 * An implementation of {@link org.apache.tapestry.IBinding} to connect components to flow properties.
 * This binding is used automatically by the {@link org.amplafi.flow.web.resolvers.FlowAwareTemplateSourceDelegate}
 * to connect the generated flow template (and the components in the flow) to the flow properties.
 *
 * Most of the time developers are not aware nor do they explicitly use this {@link org.apache.tapestry.IBinding}.
 *
 * Putting a breakpoint in {@link #getFlowStateProperty(Class)} is a good place to start debugging any issues with
 * parameters of a component not being connected up correctly.
 *
 * @author Patrick Moore
 */
public class FlowPropertyBinding implements FlowProvider, IBinding {

    /**
     *
     */
    private static final String VALIDATORS = "validators";

    private final ValueConverter valueConverter;

    private final Location location;

    /**
     * The flow component that is the source of this binding
     */
    private IComponent root;

    /**
     * The expression used to access the binding
     */
    private String key;

    /**
     * The request cycle for the FlowProvider
     */
    private IRequestCycle cycle;

    /**
     * The Tapestry binding factory used to create simple, non-flow bindings
     */
    private BindingFactory validationBindingFactory;

    private String defaultValue;

    private String description;

    private String componentName;

    private IComponent flowComponent;

    private IBinding defaultValueBinding;

    /**
     * Constructor - Set to protected to ensure the use of the {@link FlowPropertyBindingFactory} in creation of this object.
     *
     * @param root The flow component that is the source of this binding
     * @param description A description of how the binding is used
     * @param valueConverter Used to convert the value of the binding to a specific data type
     * @param location The location of the binding
     * @param expression The expression used to access the binding
     * @param bindingFactory The Tapestry binding factory used to create simple, non-flow bindings
     * @param bindingSource TODO
     * @throws IllegalArgumentException If the expression is not populated
     */
    protected FlowPropertyBinding(IComponent root, String description, ValueConverter valueConverter, Location location, String expression,
            BindingFactory bindingFactory, BindingSource bindingSource) {

        Defense.notNull(description, "description");
        Defense.notNull(valueConverter, "valueConverter");

        this.valueConverter = valueConverter;
        this.location = location;
        this.description = description;
        // Save instance variables
        this.root = root;

        if (expression == null) {
            throw new IllegalArgumentException("no expression to evaluate");
        }
        int equalsIndex = expression.indexOf('=');
        // also check to make sure the '=' is not the last character.
        if (equalsIndex >= 0 && equalsIndex < expression.length()-1) {
            this.key = expression.substring(0, equalsIndex);
            // the expression came in the form "fprop:key=some-default-value"
            int componentIndicator = expression.indexOf('@', equalsIndex+1);
            if ( componentIndicator > equalsIndex) {
                componentName = expression.substring(equalsIndex+1, componentIndicator);
                this.defaultValue = expression.substring(componentIndicator+1);
            } else {
                this.defaultValue = expression.substring(equalsIndex+1);
            }
            if ( isNotBlank(componentName)) {
                flowComponent = root.getComponent(componentName);
            } else {
                flowComponent = root;
            }
            defaultValueBinding = bindingSource.createBinding(flowComponent,
                                                              description+ " default binding. Component Name ='"+componentName+"' defaultValue='"+defaultValue+"'", defaultValue,
                                                              BindingConstants.OGNL_PREFIX, location);
        } else {
            this.key = expression;
            // just for explicitness
            this.defaultValue = null;
        }
        this.cycle = root.getPage().getRequestCycle();
        this.validationBindingFactory = bindingFactory;
    }

    public Location getLocation() {
        return location;
    }

    /**
     * Returns a value indicating whether or not the value of this binding is invariant or not. This implementation will always return {@code false}
     * because at this time flow properties can always be modified.
     *
     * @return A boolean value indicating whether or not this binding's value is invariant
     */
    @Override
    public boolean isInvariant() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public Object getObject(Class type) {
        Defense.notNull(type, "type");

        Object raw = getFlowStateProperty(type);

        try {
            return valueConverter.coerceValue(raw, type);
        } catch (Exception ex) {
            // String message = BindingMessages.convertObjectError(this, ex);

            if (raw != null) {
                throw new BindingException("Error converting a "+raw.getClass().getName()+" to "+type.getName()+" toString() of value ="+raw,
                                           this.root, location, this, ex);
            } else {
                throw new BindingException("Error converting a null", this.root, location, this, ex);
            }
        }
    }

    /**
     * Gets the value of this binding.
     *
     * @return The value of the binding
     * @throws BindingException If there is an issue (validity, well-formed) with the value of the binding
     */
    @Override
    public Object getObject() throws BindingException {
        return getFlowStateProperty(null);
    }

    protected Object getFlowStateProperty(Class<?> expected) {
        Object result = null;
        // Determine if there is a flow state to get the value from, if not just return defaultValue
        FlowState flowState = getFlowToUse();
        if (flowState != null) {
            addValidation(flowState.getCurrentActivity(), cycle.renderStackPeek());
            try {
                result = flowState.getPropertyAsObject(key, expected);
            } catch (RuntimeException e) {
                if (e.getCause() instanceof ValidatorException) {
                    throw new BindingException(e.getMessage(), this, e.getCause());
                } else {
                    throw new BindingException(e.getMessage(), this, e);
                }
            }
        }
        if (result == null && defaultValueBinding != null) {
            result = defaultValueBinding.getObject(expected);
        }
        return result;
    }

    /**
     * Sets the value of the binding, if allowed.
     *
     * @param value The new value of the binding
     * @throws IllegalStateException If there is no flow attached to the binding
     * @throws BindingException If the value is not assignable to the specified type
     */
    @Override
    public void setObject(Object value) {

        // Check that we have a flow to set the value to
        FlowState flowState = getFlowToUse();
        if (flowState == null) {
            throw new IllegalStateException("no attached flow - cannot set value");
        }
        flowState.setPropertyAsObject(key, value);
    }

    /**
     * Specific implementation of the Object.toString method.
     *
     * @return A string representation of this binding
     */
    @Override
    public String toString() {
        return super.toString() + "[expression=" + this.key + (defaultValue==null?"]": (" defaultValue="+this.defaultValue+"]"));
    }

    /**
     * Get the {@link FlowState} object this binding uses.
     *
     * @return The FlowState object this binding uses
     */
    public FlowState getFlowToUse() {
        return ((FlowProvider)this.root).getFlowToUse();
    }

    /**
     * Get the {@link FlowManagement} object this binding uses.
     *
     * @return The FlowManager object this binding uses
     */
    public FlowManagement getFlowManagement() {
        return ((FlowProvider)this.root).getFlowManagement();
    }

    private void addValidation(FlowActivity activity, IRender render) {
        if (render instanceof AbstractFormComponent && render instanceof ValidatableField) {
            AbstractFormComponent formComponent = (AbstractFormComponent) render;
            if (formComponent.getBinding(VALIDATORS) == null) {
                FlowPropertyDefinition definition = activity.getPropertyDefinition(this.key);
                String validators = definition == null? null: definition.getValidators();
                if (validators != null) {
                    IBinding binding = this.validationBindingFactory.createBinding(formComponent, "", validators, null);
                    formComponent.setBinding(VALIDATORS, binding);
                }
            }
        }
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
