/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class ExporterPositionsDistributionTest {

  private ExporterStateDistributionService exporterStateDistributionService;
  private Map<String, Long> exporterPositions;
  private SimplePartitionMessageService partitionMessagingService;

  @Before
  public void setup() {
    exporterPositions = new HashMap<>();
    partitionMessagingService = new SimplePartitionMessageService();
    exporterStateDistributionService =
        new ExporterStateDistributionService(
            exporterPositions::put, partitionMessagingService, "topic");
  }

  @Test
  public void shouldSubscribeForGivenTopic() {
    // given

    // when
    exporterStateDistributionService.subscribeForExporterState(Runnable::run);

    // then
    assertThat(partitionMessagingService.consumers).containsKey("topic");
  }

  @Test
  public void shouldConsumeExporterMessage() {
    // given
    final var exporterPositionsMessage = new ExporterStateDistributeMessage();
    exporterPositionsMessage.putExporter("elastic", 123);
    exporterPositionsMessage.putExporter("metric", 345);
    exporterStateDistributionService.subscribeForExporterState(Runnable::run);

    // when
    exporterStateDistributionService.distributeExporterState(exporterPositionsMessage);

    // then
    assertThat(exporterPositions).containsEntry("elastic", 123L).containsEntry("metric", 345L);
  }

  @Test
  public void shouldRemoveSubscriptionOnClose() throws Exception {
    // given
    final var exporterPositionsMessage = new ExporterStateDistributeMessage();
    exporterPositionsMessage.putExporter("elastic", 123);
    exporterPositionsMessage.putExporter("metric", 345);
    exporterStateDistributionService.subscribeForExporterState(Runnable::run);

    // when
    exporterStateDistributionService.close();

    // then
    assertThat(partitionMessagingService.consumers).isEmpty();
  }
}
