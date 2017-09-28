package org.camunda.optimize.test.performance.data.generation;

import org.camunda.optimize.test.performance.data.generation.impl.AuthorizationArrangementDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.ChangeContactDataDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.ContactInterviewDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.DocumentCheckHandlingDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.ExportInsuranceDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.ExtendedOrderDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.HiringProcessDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.InvoiceDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.LeadQualificationDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.MultiParallelDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.OrderConfirmationDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.PickUpHandlingDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.ProcessRequestDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.ReviewCaseDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.SimpleServiceTaskDataGenerator;
import org.camunda.optimize.test.performance.data.generation.impl.TransshipmentArrangementDataGenerator;

import java.util.ArrayList;
import java.util.List;

public class DataGeneratorProvider {

  private List<DataGenerator> dataGenerators;
  private int totalInstanceCount;

  public DataGeneratorProvider(int totalInstanceCount) {
    this.totalInstanceCount = totalInstanceCount;
    init();
  }

  private void init() {
    dataGenerators = new ArrayList<>();
    dataGenerators.add(new HiringProcessDataGenerator());
    dataGenerators.add(new ExtendedOrderDataGenerator());
    dataGenerators.add(new ContactInterviewDataGenerator());
    dataGenerators.add(new SimpleServiceTaskDataGenerator("SimpleServiceTaskProcess1"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator("SimpleServiceTaskProcess2"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator("SimpleServiceTaskProcess3"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator("SimpleServiceTaskProcess4"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator("SimpleServiceTaskProcess5"));
    dataGenerators.add(new LeadQualificationDataGenerator());
    dataGenerators.add(new InvoiceDataGenerator());
    dataGenerators.add(new OrderConfirmationDataGenerator());
    dataGenerators.add(new MultiParallelDataGenerator());
    dataGenerators.add(new TransshipmentArrangementDataGenerator());
    dataGenerators.add(new PickUpHandlingDataGenerator());
    dataGenerators.add(new AuthorizationArrangementDataGenerator());
    dataGenerators.add(new ChangeContactDataDataGenerator());
    dataGenerators.add(new ProcessRequestDataGenerator());
    dataGenerators.add(new ExportInsuranceDataGenerator());
    dataGenerators.add(new DocumentCheckHandlingDataGenerator());
    dataGenerators.add(new ReviewCaseDataGenerator());
    setInstanceNumberToGenerateForEachGenerator();
  }

  private void setInstanceNumberToGenerateForEachGenerator() {
    int nGenerators = dataGenerators.size();
    int stepSize = totalInstanceCount / nGenerators;
    int missingCount = totalInstanceCount % nGenerators;
    dataGenerators.forEach(
      generator -> generator.setInstanceCountToGenerate(stepSize)
    );
    dataGenerators.get(0).addToInstanceCount(missingCount);
  }

  public int getTotalDataGeneratorCount() {
    return dataGenerators.size();
  }

  public List<DataGenerator> getDataGenerators() {
    return dataGenerators;
  }
}
