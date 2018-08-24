package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataProcessInstanceDto;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;


public class CSVUtilsTest {

  @Test
  public void testMapping() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    //when
    List<String[]> result = CSVUtils.map(toMap);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(9));
  }

}