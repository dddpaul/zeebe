package org.camunda.operate.util;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.writer.WorkflowInstanceWriter;
import org.camunda.operate.po.WorkflowInstanceEntity;
import org.camunda.operate.po.WorkflowInstanceState;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@ConditionalOnProperty(name= OperateProperties.PREFIX + ".demoData", havingValue="true")
@Profile("elasticsearch")
public class DemoDataGenerator {

  @Autowired
  private WorkflowInstanceWriter workflowInstanceWriter;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @PostConstruct
  public void generateESData() {

    Random random = new Random();

    final long count = workflowInstanceReader.queryWorkflowInstancesCount(new WorkflowInstanceQueryDto());
    if (count == 0) {

      List<WorkflowInstanceEntity> workflowInstances = new ArrayList<>();
      for (int i = 0; i < random.nextInt(100) + 500; i++) {

        WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
        workflowInstance.setId(UUID.randomUUID().toString());
        workflowInstance.setBusinessKey("process" + random.nextInt(10));
        workflowInstance.setStartDate(DateUtil.getRandomStartDate());
        final OffsetDateTime endDate = DateUtil.getRandomEndDate(true);
        workflowInstance.setEndDate(endDate);
        workflowInstance.setState(endDate == null ? WorkflowInstanceState.ACTIVE : WorkflowInstanceState.COMPLETED);

        workflowInstance.setWorkflowDefinitionId(UUID.randomUUID().toString());
        workflowInstances.add(workflowInstance);
      }

      workflowInstanceWriter.persistWorkflowInstances(workflowInstances);
    }

  }

}
