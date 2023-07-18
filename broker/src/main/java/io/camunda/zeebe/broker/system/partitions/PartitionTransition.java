/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.system.partitions.impl.RecoverablePartitionTransitionException;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

public interface PartitionTransition {

  /**
   * Transitions to follower asynchronously by closing the current partition's components and
   * opening a follower partition.
   *
   * @param currentTerm the current term on which the transition happens
   * @return an ActorFuture to be completed when the transition is complete. Completed exceptionally
   *     with {@link CancelledPartitionTransition} if the partition was cancelled.
   */
  ActorFuture<Void> toFollower(final long currentTerm);

  /**
   * Transitions to leader asynchronously by closing the current partition's components and opening
   * a leader partition.
   *
   * @param currentTerm the current term on which the transition happens
   * @return an ActorFuture to be completed when the transition is complete. Completed exceptionally
   *     * with {@link CancelledPartitionTransition} if the partition was cancelled.
   */
  ActorFuture<Void> toLeader(final long currentTerm);

  /**
   * Closes the current partition's components asynchronously.
   *
   * @return an ActorFuture completed when the transition is complete. Completed exceptionally *
   *     with {@link CancelledPartitionTransition} if the partition was cancelled.
   * @param term
   */
  ActorFuture<Void> toInactive(final long term);

  /**
   * Sets the ConcurrencyControl through which tasks are executed.
   *
   * @param concurrencyControl the concurrency control
   */
  void setConcurrencyControl(ConcurrencyControl concurrencyControl);

  /**
   * Sets the transition context
   *
   * @param transitionContext the context to be used
   */
  void updateTransitionContext(PartitionTransitionContext transitionContext);

  /** @return null if transition is healthy or a {@link HealthIssue} if not. */
  HealthIssue getHealthIssue();

  /** Used to exceptionally complete transition futures when the transition was cancelled. */
  final class CancelledPartitionTransition extends RecoverablePartitionTransitionException {

    public CancelledPartitionTransition() {
      super("Partition transition was cancelled");
    }
  }
}
