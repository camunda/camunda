/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import io.camunda.zeebe.protocol.record.value.ImmutableCommandDistributionRecordValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.FieldPredicates;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.range.LongRangeRandomizer;
import org.jeasy.random.randomizers.registry.CustomRandomizerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Record} factory which produces randomized records deterministically. A seed can be given
 * on construction to reproduce the same records. On failure, the seed can be fetched via {@link
 * #getSeed()}. By default, calling {@link ProtocolFactory#ProtocolFactory()} will always return a
 * factory which produces the same records. This is useful for reproducible tests, such that running
 * the same test twice (regardless of the environment, e.g. CI or locally) will produce the same
 * results.
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
  private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolFactory.class);
  private static final String PROTOCOL_PACKAGE_NAME = Record.class.getPackage().getName() + "*";

  private final CustomRandomizerRegistry randomizerRegistry;
  private final EasyRandomParameters parameters;
  private final EasyRandom random;

  /**
   * Every call to this constructor will always return a factory which produces the exact same
   * record. This is useful for reproducible tests. If you want a different behavior, then you can
   * use {@link ProtocolFactory#ProtocolFactory(long)} and pass something like {@link
   * ThreadLocalRandom#nextLong()}.
   */
  public ProtocolFactory() {
    this(0);
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
    parameters = getDefaultParameters().seed(seed).scanClasspathForConcreteTypes(true);
    random = new EasyRandom(parameters);
    registerRandomizers();
  }

  /**
   * @return a stream of random records
   */
  public <T extends RecordValue> Stream<Record<T>> generateRecords() {
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
  public <T extends RecordValue> Stream<Record<T>> generateRecords(
      final UnaryOperator<Builder<T>> modifier) {
    return Stream.generate(() -> generateRecord(modifier));
  }

  /**
   * Generates a stream of records, one record for each known {@link ValueType} (except the values
   * created by SBE, i.e. NULL_VAL and SBE_UNKNOWN).
   *
   * @return a stream of records, one for each value type
   */
  public <T extends RecordValue> Stream<Record<T>> generateForAllValueTypes() {
    return generateForAllValueTypes(UnaryOperator.identity());
  }

  /**
   * Generates a stream of records, one record for each known {@link ValueType} (except the values
   * created by SBE, i.e. NULL_VAL and SBE_UNKNOWN).
   *
   * @return a stream of records, one for each value type
   * @throws NullPointerException if {@code modifier} is null
   */
  public <T extends RecordValue> Stream<Record<T>> generateForAllValueTypes(
      final UnaryOperator<Builder<T>> modifier) {
    return ValueTypeMapping.getAcceptedValueTypes().stream()
        .map(valueType -> generateRecord(valueType, modifier));
  }

  /**
   * @return a random record with a random value type
   */
  public <T extends RecordValue> Record<T> generateRecord() {
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
  public <T extends RecordValue> Record<T> generateRecord(
      final UnaryOperator<Builder<T>> modifier) {
    final var valueType = random.nextObject(ValueType.class);
    return generateRecord(valueType, modifier);
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
  public <T extends RecordValue> Record<T> generateRecord(final ValueType valueType) {
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
  public <T extends RecordValue> Record<T> generateRecord(
      final ValueType valueType, final UnaryOperator<Builder<T>> modifier) {
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
   * @return the seed used when creating this factory
   */
  public long getSeed() {
    return parameters.getSeed();
  }

  private void registerRandomizers() {
    findProtocolTypes().forEach(this::registerProtocolType);
    randomizerRegistry.registerRandomizer(Object.class, new RawObjectRandomizer());

    // restrict longs to be between 0 and max value - this is because many of our long properties
    // are timestamps, which are semantically between 0 and any future time
    randomizerRegistry.registerRandomizer(
        Long.class, new LongRangeRandomizer(0L, Long.MAX_VALUE, getSeed()));
    randomizerRegistry.registerRandomizer(
        long.class, new LongRangeRandomizer(0L, Long.MAX_VALUE, getSeed()));

    // never use NULL_VAL or SBE_UNKNOWN for ValueType or RecordType
    randomizerRegistry.registerRandomizer(
        ValueType.class,
        new EnumRandomizer<>(
            getSeed(), ValueTypeMapping.getAcceptedValueTypes().toArray(ValueType[]::new)));

    final var excludedRecordTypes = EnumSet.of(RecordType.NULL_VAL, RecordType.SBE_UNKNOWN);
    final var recordTypes = EnumSet.complementOf(excludedRecordTypes);
    randomizerRegistry.registerRandomizer(
        RecordType.class, new EnumRandomizer<>(getSeed(), recordTypes.toArray(RecordType[]::new)));

    randomizerRegistry.registerRandomizer(
        ImmutableCommandDistributionRecordValue.class,
        () -> {
          final var valueType = random.nextObject(ValueType.class);
          final var typeInfo = ValueTypeMapping.get(valueType);
          return ImmutableCommandDistributionRecordValue.builder()
              .withPartitionId(random.nextInt())
              .withValueType(valueType)
              .withIntent(random.nextObject(typeInfo.getIntentClass()))
              .withCommandValue(generateObject(typeInfo.getValueClass()))
              .build();
        });
  }

  private void registerProtocolType(final ClassInfo abstractType) {
    final var implementations =
        abstractType
            .getClassesImplementing()
            // grab only the immutable implementations
            .filter(info -> info.hasAnnotation(ImmutableProtocol.Type.class))
            .directOnly()
            .loadClasses();

    if (implementations.isEmpty()) {
      LOGGER.warn(
          "No implementations found for abstract protocol type {}; random generation will not be possible for this type",
          abstractType.getName());
      return;
    }

    final var implementation = implementations.get(0);
    if (implementations.size() > 1) {
      LOGGER.warn(
          "More than one implementation found for abstract protocol type {}; random generation will use the first one: {}",
          abstractType.getName(),
          implementation.getName());
    }

    randomizerRegistry.registerRandomizer(
        abstractType.loadClass(), () -> random.nextObject(implementation));
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

  private <T extends RecordValue> Record<T> generateImmutableRecord(
      final ValueType valueType, final UnaryOperator<Builder<T>> modifier) {
    Objects.requireNonNull(valueType, "must specify a value type");
    Objects.requireNonNull(modifier, "must specify a builder modifier");

    final var typeInfo = ValueTypeMapping.get(valueType);
    final var intent = random.nextObject(typeInfo.getIntentClass());
    final var value = generateObject(typeInfo.getValueClass());
    final var seedRecord = random.nextObject(Record.class);

    //noinspection unchecked
    final Builder<T> builder =
        ImmutableRecord.builder()
            .from(seedRecord)
            .withValueType(valueType)
            .withValue(value)
            .withIntent(intent);

    return Objects.requireNonNull(modifier.apply(builder), "must return a non null builder")
        .build();
  }

  // visible for testing
  static ClassInfoList findProtocolTypes() {
    return new ClassGraph()
        .acceptPackages(PROTOCOL_PACKAGE_NAME)
        .enableAnnotationInfo()
        .scan()
        .getAllInterfaces()
        .filter(info -> info.hasAnnotation(ImmutableProtocol.class))
        .directOnly();
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
