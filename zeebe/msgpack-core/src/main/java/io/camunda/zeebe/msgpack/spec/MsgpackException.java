/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.spec;

public class MsgpackException extends RuntimeException {
  private static final long serialVersionUID = -514144849724509376L;

  public MsgpackException(final String message) {
    super(message);
  }

  public MsgpackException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public MsgpackException(final Throwable cause) {
    super(cause);
  }

  public MsgpackException(
      final String message,
      final Throwable cause,
      final boolean enableSuppression,
      final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
