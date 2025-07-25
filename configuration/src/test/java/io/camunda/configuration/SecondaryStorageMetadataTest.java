import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
public class SecondaryStorageMetadataTest {
  @Nested
  @TestPropertySource(properties = {})
  class WithOnlyUnifiedConfigSet {
    // TODO
  }

  @Nested
  @TestPropertySource(properties = {})
  class WithOnlyLegacySet {
    // TODO
  }

  @Nested
  @TestPropertySource(properties = {})
  class WithNewAndLegacySet {
    // TODO
  }
}
