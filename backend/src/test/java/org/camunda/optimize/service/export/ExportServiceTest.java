package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataProcessInstanceDto;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;


public class ExportServiceTest {

  @Test
  public void writeRawDataToBytes() throws IOException {
    //given
    ExportService exportService = new ExportService();
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    byte[] csvContent = exportService.writeRawDataToBytes(toMap);

    Path path = Paths.get(this.getClass().getResource("/csv/raw_data.csv").getPath());
    byte[] expectedContent = Files.readAllBytes(path);;

    assertArrayEquals(csvContent, expectedContent);
  }

}