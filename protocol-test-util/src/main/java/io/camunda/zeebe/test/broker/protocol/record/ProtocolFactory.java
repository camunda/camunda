/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.record;

import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping;
import io.camunda.zeebe.protocol.util.ProtocolTypeMapping.Mapping;
import io.camunda.zeebe.protocol.util.ValueTypeMapping;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.FieldPredicates;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.registry.CustomRandomizerRegistry;

/**
 * A {@link Record} factory which produces randomized records deterministically. A seed can be given
 * on construction to reproduce the same records. On failure, the seed can be fetched via {@link
 * #getSeed()}.
 *
 * <p>Every property is fully randomized and cannot be relied upon semantically, except the value
 * type, the value, and the intent. These will always all match the implicit assumptions of the
 * protocol. For example, if the {@link ValueType} is {@link ValueType#JOB}, then the record's value
 * is going to be an instance of {@link io.camunda.zeebe.protocol.record.value.JobRecordValue}, and
 * the intent is going to be an instance of {@link
 * io.camunda.zeebe.protocol.record.intent.JobIntent}. This is done to enable using this factory
 * with tests that expect to serialize and deserialize the records.
 */
@SuppressWarnings("java:S1452")
public final class ProtocolFactory {
  private final CustomRandomizerRegistry randomizerRegistry;
  private final EasyRandomParameters parameters;
  private final EasyRandom random;

  /**
   * Every call to this constructor will always return a factory which will produce different
   * records. If you wish to reproduce past results, consider using {@link
   * ProtocolFactory#ProtocolFactory(long)}.
   */
  public ProtocolFactory() {
    this(ThreadLocalRandom.current().nextLong());
  }

  /**
   * Returns a factory with the given seed for the initial randomization. This enables you to
   * reproduce past results if you know the seed. Note that you will need to also effectively
   * reproduce the same steps as in the past; the seed by itself is not enough. So if you have a
   * seed, and want to reproduce the 10th record generated, you will need to generate 10 records
   * again.
   *
   * @param seed the seed to use
   */
  public ProtocolFactory(final long seed) {
    randomizerRegistry = new CustomRandomizerRegistry();
    parameters = getDefaultParameters().seed(seed);
    random = new EasyRandom(parameters);
    registerRandomizers();
  }

  /** @return a random record with a random value type */
  public Record<RecordValue> generateRecord() {
    return generateRecord(UnaryOperator.identity());
  }

  /**
   * Generates a record with the given modifier applied to its builder as the last step in the
   * generation process.
   *
   * @param modifier applied to the builder after all the properties have been filled as the last
   *     step; cannot be null
   * @return a randomly generated record
   * @throws NullPointerException if {@code modifier} is null
   */
  public Record<RecordValue> generateRecord(final UnaryOperator<Builder<RecordValue>> modifier) {
    final var valueType = random.nextObject(ValueType.class);
    return generateRecord(valueType, modifier);
  }

  /** @return a stream of random records */
  public Stream<Record<RecordValue>> generateRecords() {
    return generateRecords(UnaryOperator.identity());
  }

  /**
   * Generates a modifiable stream of records.
   *
   * @param modifier applied to the builder after all the properties have been filled as the last
   *     step; cannot be null
   * @return a stream of random records
   * @throws NullPointerException if modifier is null
   */
  public Stream<Record<RecordValue>> generateRecords(
      final UnaryOperator<Builder<RecordValue>> modifier) {
    return Stream.generate(() -> generateRecord(modifier));
  }

  /**
   * Generates a record with the given {@code valueType}. The value and intent properties will be
   * picked from the appropriate types based on the given {@code valueType}. This means, if you pass
   * {@link ValueType#ERROR}, the value will be of type {@link
   * io.camunda.zeebe.protocol.record.value.ErrorRecordValue}, and the intent of type {@link
   * io.camunda.zeebe.protocol.record.intent.ErrorIntent}. Each of these will, of course, be
   * randomly generated as well.
   *
   * @param valueType the expected value type of the record
   * @return a randomized record with the given value type, with the value and intent being of the
   *     expected types
   * @throws NullPointerException if {@code valueType} is null
   */
  public Record<RecordValue> generateRecord(final ValueType valueType) {
    return generateRecord(valueType, UnaryOperator.identity());
  }

  /**
   * Generates a record with the given {@code valueType}. The value and intent properties will be
   * picked from the appropriate types based on the given {@code valueType}. This means, if you pass
   * {@link ValueType#ERROR}, the value will be of type {@link
   * io.camunda.zeebe.protocol.record.value.ErrorRecordValue}, and the intent of type {@link
   * io.camunda.zeebe.protocol.record.intent.ErrorIntent}. Each of these will, of course, be
   * randomly generated as well.
   *
   * <p>The given modifier is applied to the final builder as the last step of the generation.
   *
   * @param valueType the expected value type of the record
   * @param modifier applied to the builder after all the properties have been filled as the last *
   *     step; cannot be null
   * @return a randomized record with the given value type, with the value and intent being of the
   *     expected types
   * @throws NullPointerException if {@code modifier} is null or if {@code valueType} is null
   */
  public Record<RecordValue> generateRecord(
      final ValueType valueType, final UnaryOperator<Builder<RecordValue>> modifier) {
    return generateImmutableRecord(valueType, modifier);
  }

  /**
   * Generates a random object. Can be used, for example, to generate a random {@link
   * io.camunda.zeebe.protocol.record.value.ErrorRecordValue}.
   *
   * <p>This is a convenience method if you wish to generate further random objects but want
   * everything to be reproducible, i.e. using the same seed/randomization.
   *
   * @param objectClass the class of the object ot generate
   * @throws NullPointerException if {@code objectClass} is null
   */
  public <T> T generateObject(final Class<T> objectClass) {
    return random.nextObject(objectClass);
  }

  /**
   * Generates a stream of records, one record for each known {@link ValueType} (except the values
   * created by SBE, i.e. NULL_VAL and SBE_UNKNOWN).
   *
   * @return a stream of records, one for each value type
   */
  public Stream<Record<RecordValue>> generateForAllValueTypes() {
    return generateForAllValueTypes(UnaryOperator.identity());
  }

  /**
   * Generates a stream of records, one record for each known {@link ValueType} (except the values
   * created by SBE, i.e. NULL_VAL and SBE_UNKNOWN).
   *
   * @return a stream of records, one for each value type
   * @throws NullPointerException if {@code modifier} is null
   */
  public Stream<Record<RecordValue>> generateForAllValueTypes(
      final UnaryOperator<Builder<RecordValue>> modifier) {
    return ValueTypeMapping.getAcceptedValueTypes().stream()
        .map(valueType -> generateRecord(valueType, modifier));
  }

  /** @return the seed used when creating this factory */
  public long getSeed() {
    return parameters.getSeed();
  }

  private void registerRandomizers() {
    ProtocolTypeMapping.forEach(this::registerProtocolTypeRandomizer);
    randomizerRegistry.registerRandomizer(Object.class, new RawObjectRandomizer());
    randomizerRegistry.registerRandomizer(
        ValueType.class,
        new EnumRandomizer<>(
            parameters.getSeed(),
            ValueTypeMapping.getAcceptedValueTypes().toArray(ValueType[]::new)));
  }

  private void registerProtocolTypeRandomizer(final Mapping<?> typeMapping) {
    randomizerRegistry.registerRandomizer(
        typeMapping.getAbstractClass(), () -> random.nextObject(typeMapping.getConcreteClass()));
  }

  private EasyRandomParameters getDefaultParameters() {
    // as we will ensure value/intent/valueType having matching types, omit them from the
    // randomization process - we will do that individually afterwards
    final Predicate<Field> excludedRecordFields =
        FieldPredicates.inClass(ImmutableRecord.class)
            .and(
                FieldPredicates.named("value")
                    .or(FieldPredicates.named("intent"))
                    .or(FieldPredicates.named("valueType")));

    return new EasyRandomParameters()
        .randomizerRegistry(randomizerRegistry)
        // we have to bypass the setters since our types are almost exclusively immutable
        .bypassSetters(true)
        // allow empty collections, and only generate up to 5 items
        .collectionSizeRange(0, 5)
        // as we have nested types in our protocol, let's give a generous depth here, but let's
        // still limit it to avoid errors/issues with nested collections
        .randomizationDepth(8)
        .excludeField(excludedRecordFields);
  }

  private Record<RecordValue> generateImmutableRecord(
      final ValueType valueType, final UnaryOperator<Builder<RecordValue>> modifier) {
    Objects.requireNonNull(valueType, "must specify a value type");
    Objects.requireNonNull(modifier, "must specify a builder modifier");

    final var typeInfo = ValueTypeMapping.get(valueType);
    final var intent = random.nextObject(typeInfo.getIntentClass());
    final var value = generateObject(typeInfo.getValueClass());
    final var seedRecord = random.nextObject(Record.class);

    //noinspection unchecked
    final Builder<RecordValue> builder =
        ImmutableRecord.builder()
            .from(seedRecord)
            .withValueType(valueType)
            .withValue(value)
            .withIntent(intent);

    return Objects.requireNonNull(modifier.apply(builder), "must return a non null builder")
        .build();
  }

  /**
   * A few of the protocol types refer to {@code Map<String, Object>}. easy-random doesn't really
   * know how to randomize the object type, and instead just creates raw {@link Object} instances.
   * This not only doesn't really simulate the expected data set, but it's not serializable, which
   * would prevent this class from being used in any kind of serialization context (e.g. exporters)
   *
   * <p>To circumvent this, we randomize the type to some easy to serialize and randomize type, and
   * return that.
   *
   * <p>NOTE: floats and doubles were omitted on purpose as Jackson has trouble deserializing them,
   * and instead will always deserialize them as {@link java.math.BigDecimal}. This is a regression,
   * so we could think about adding them back once that's fixed, but it didn't seem super important
   * at the moment.
   */
  private final class RawObjectRandomizer implements Randomizer<Object> {
    private final Class<?>[] variableTypes = new Class[] {Boolean.class, Long.class, String.class};

    @Override
    public Object getRandomValue() {
      final var variableType = variableTypes[random.nextInt(variableTypes.length)];
      return random.nextObject(variableType);
    }
  }
}
