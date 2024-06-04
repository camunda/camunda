/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.Tuple;
import java.math.BigInteger;
import org.elasticsearch.common.text.Text;
import org.junit.jupiter.api.Test;

public class SortValuesWrapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testConvertSortValuesString() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString("testString"), String.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo("testString");
    assertThat(result[0].getClass()).isEqualTo(String.class);
  }

  @Test
  public void testConvertSortValuesText() {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper("\"testString\"", Text.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(new Text("testString"));
    assertThat(result[0].getClass()).isEqualTo(Text.class);
  }

  @Test
  public void testConvertSortValuesLong() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Long.MAX_VALUE), Long.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Long.MAX_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Long.class);
  }

  @Test
  public void testConvertSortValuesInteger() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(123), Integer.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(123);
    assertThat(result[0].getClass()).isEqualTo(Integer.class);
  }

  @Test
  public void testConvertSortValuesShort() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Short.MIN_VALUE), Short.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Short.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Short.class);
  }

  @Test
  public void testConvertSortValuesByte() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Byte.MIN_VALUE), Byte.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Byte.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Byte.class);
  }

  @Test
  public void testConvertSortValuesDouble() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Double.MIN_VALUE), Double.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Double.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Double.class);
  }

  @Test
  public void testConvertSortValuesFloat() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Float.MIN_VALUE), Float.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Float.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Float.class);
  }

  @Test
  public void testConvertSortValuesBoolean() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(false), Boolean.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(false);
    assertThat(result[0].getClass()).isEqualTo(Boolean.class);
  }

  @Test
  public void testConvertSortValuesBigInteger() throws JsonProcessingException {

    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(BigInteger.TWO), BigInteger.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(BigInteger.TWO);
    assertThat(result[0].getClass()).isEqualTo(BigInteger.class);
  }

  @Test
  public void testConvertSortValuesBadType() throws JsonProcessingException {
    final Tuple<String, String> tupleVal = new Tuple<>("left", "right");
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(tupleVal), Tuple.class)
    };

    assertThrows(
        OperateRuntimeException.class,
        () -> SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper));
  }

  @Test
  public void testConvertSortValuesRecordType() throws JsonProcessingException {
    record Result(String id) {}
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(new Result("foo")), Result.class)
    };

    assertThrows(
        OperateRuntimeException.class,
        () -> SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper));
  }

  @Test
  public void testCreateFromString() throws JsonProcessingException {
    final Object[] sortValues = {"testString"};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString("testString"));
    assertThat(result[0].getValueType()).isEqualTo(String.class);
  }

  @Test
  public void testCreateFromText() {
    final Object[] sortValues = {new Text("testString")};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    // The object mapper does not have a module registered that correctly serializes Text types
    // (the result is a string that reads "{fragment:true}")
    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValueType()).isEqualTo(Text.class);
  }

  @Test
  public void testCreateFromLong() throws JsonProcessingException {
    final Object[] sortValues = {Long.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Long.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Long.class);
  }

  @Test
  public void testCreateFromInteger() throws JsonProcessingException {
    final Object[] sortValues = {Integer.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Integer.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Integer.class);
  }

  @Test
  public void testCreateFromShort() throws JsonProcessingException {
    final Object[] sortValues = {Short.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Short.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Short.class);
  }

  @Test
  public void testCreateFromByte() throws JsonProcessingException {
    final Object[] sortValues = {Byte.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Byte.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Byte.class);
  }

  @Test
  public void testCreateFromDouble() throws JsonProcessingException {
    final Object[] sortValues = {Double.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Double.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Double.class);
  }

  @Test
  public void testCreateFromFloat() throws JsonProcessingException {
    final Object[] sortValues = {Float.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Float.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Float.class);
  }

  @Test
  public void testCreateFromBoolean() throws JsonProcessingException {
    final Object[] sortValues = {true};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(true));
    assertThat(result[0].getValueType()).isEqualTo(Boolean.class);
  }

  @Test
  public void testCreateFromBigInteger() throws JsonProcessingException {
    final Object[] sortValues = {BigInteger.TWO};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(BigInteger.TWO));
    assertThat(result[0].getValueType()).isEqualTo(BigInteger.class);
  }

  @Test
  public void testCreateFromTypeNotAllowedInDeserialization() throws JsonProcessingException {
    final Tuple<String, String> tupleVal = new Tuple<>("left", "right");
    final Object[] sortValues = {tupleVal};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(tupleVal));
    assertThat(result[0].getValueType()).isEqualTo(Tuple.class);
  }

  @Test
  public void testCreateFromRecordNotAllowedInDeserialization() throws JsonProcessingException {
    record Result(String id) {}
    final var recordVal = new Result("id");
    final Object[] sortValues = {recordVal};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(recordVal));
    assertThat(result[0].getValueType()).isEqualTo(Result.class);
  }
}
