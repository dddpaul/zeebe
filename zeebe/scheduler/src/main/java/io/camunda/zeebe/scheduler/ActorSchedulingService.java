/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Service interface to schedule an actor (without exposing the full interface of {@code
 * ActorScheduler}
 */
public interface ActorSchedulingService {
  ActorFuture<Void> submitActor(final Actor actor);

  ActorFuture<Void> submitActor(final Actor actor, SchedulingHints schedulingHints);
}
