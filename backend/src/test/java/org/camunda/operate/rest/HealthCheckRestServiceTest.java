package org.camunda.operate.rest;

import org.camunda.operate.Application;
import org.camunda.operate.TestApplication;
import org.camunda.operate.rest.dto.HealthStateDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.TestTimedOutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Svetlana Dorokhova.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class HealthCheckRestServiceTest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void testHealthState() {
    final ResponseEntity<HealthStateDto> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, HealthStateDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getState()).isEqualTo("OK");
  }

}
