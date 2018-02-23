package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class RawDataHelper {
  public static final String FIXED_TIME = "2018-02-23T14:31:08.048+01:00";
  public static final String FIXED_TIME_VARIABLE = "2018-02-23T12:31:08.048+01:00";

  public static List<RawDataProcessInstanceDto> getRawDataProcessInstanceDtos() {
    List<RawDataProcessInstanceDto> toMap = new ArrayList<>();
    RawDataProcessInstanceDto instance_1 = new RawDataProcessInstanceDto();
    instance_1.setProcessDefinitionId("test_id");
    instance_1.setProcessDefinitionKey("test_key");
    instance_1.setStartDate(OffsetDateTime.parse(FIXED_TIME));
    instance_1.setEndDate(OffsetDateTime.parse(FIXED_TIME));
    Map<String, Object> vars_1 = new HashMap<>();
    vars_1.put("1", "test");
    instance_1.setVariables(vars_1);
    toMap.add(instance_1);

    RawDataProcessInstanceDto instance_2 = new RawDataProcessInstanceDto();
    Map<String, Object> vars_2 = new HashMap<>();
    vars_2.put("2", OffsetDateTime.parse(FIXED_TIME_VARIABLE));
    instance_2.setVariables(vars_2);
    toMap.add(instance_2);

    RawDataProcessInstanceDto instance_3 = new RawDataProcessInstanceDto();
    toMap.add(instance_3);
    return toMap;
  }
}
