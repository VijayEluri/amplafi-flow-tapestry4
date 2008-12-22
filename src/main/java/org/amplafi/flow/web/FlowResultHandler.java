package org.amplafi.flow.web;

import java.util.List;

import org.amplafi.flow.FlowValidationResult;
import org.amplafi.flow.FlowValidationTracking;

/**
 * Handles {@link FlowValidationResult}s in the ui layer.
 */
public interface FlowResultHandler {
    void handleFlowResult(FlowValidationResult result, BaseFlowComponent component);

    void handleValidationTrackings(List<FlowValidationTracking> trackings, BaseFlowComponent component);
}
