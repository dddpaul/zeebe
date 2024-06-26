/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.allocation;

public final class BufferAllocators {
  private static final DirectBufferAllocator DIRECT_BUFFER_ALLOCATOR = new DirectBufferAllocator();

  private BufferAllocators() {}

  public static AllocatedBuffer allocateDirect(final int capacity) {
    return DIRECT_BUFFER_ALLOCATOR.allocate(capacity);
  }
}
