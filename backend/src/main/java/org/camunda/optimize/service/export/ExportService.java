package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class ExportService {
  private Logger logger = LoggerFactory.getLogger(getClass());

  public byte[] writeRawDataToBytes (List<RawDataProcessInstanceDto> rawData) {
    List<String[]> csvStrings = CSVUtils.map(rawData);

    ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    BufferedWriter bufferedWriter = new BufferedWriter(
        new OutputStreamWriter(arrayOutputStream));
    CSVWriter csvWriter = new CSVWriter(bufferedWriter);
    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (Exception e) {
      logger.error("can't write CSV to buffer", e);
    }


    return bytes;
  }


}
