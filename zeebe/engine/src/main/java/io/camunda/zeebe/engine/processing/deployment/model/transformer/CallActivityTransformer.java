/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;

public final class CallActivityTransformer implements ModelElementTransformer<CallActivity> {

  @Override
  public Class<CallActivity> getType() {
    return CallActivity.class;
  }

  @Override
  public void transform(final CallActivity element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableCallActivity callActivity =
        process.getElementById(element.getId(), ExecutableCallActivity.class);

    transformProcessId(element, callActivity, context.getExpressionLanguage());
  }

  private void transformProcessId(
      final CallActivity element,
      final ExecutableCallActivity callActivity,
      final ExpressionLanguage expressionLanguage) {

    final ZeebeCalledElement calledElement =
        element.getSingleExtensionElement(ZeebeCalledElement.class);

    final var processId = calledElement.getProcessId();
    final var expression = expressionLanguage.parseExpression(processId);

    callActivity.setCalledElementProcessId(expression);

    final var propagateAllChildVariablesEnabled =
        calledElement.isPropagateAllChildVariablesEnabled();
    callActivity.setPropagateAllChildVariablesEnabled(propagateAllChildVariablesEnabled);

    final var propagateAllParentVariablesEnabled =
        calledElement.isPropagateAllParentVariablesEnabled();
    callActivity.setPropagateAllParentVariablesEnabled(propagateAllParentVariablesEnabled);
  }
}
