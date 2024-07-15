package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;

public final class UserDeleteProcessor implements TypedRecordProcessor<UserRecord> {}
