/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.job.processor;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;

public class JobSubscriptionRequest extends UnpackedObject {
  protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey", -1);

  protected StringProperty jobTypeProp = new StringProperty("jobType", "");

  protected LongProperty timeoutProp = new LongProperty("timeout", -1);
  protected StringProperty workerProp = new StringProperty("worker", "");

  protected IntegerProperty creditsProp = new IntegerProperty("credits", -1);

  public JobSubscriptionRequest() {
    this.declareProperty(subscriberKeyProp)
        .declareProperty(jobTypeProp)
        .declareProperty(timeoutProp)
        .declareProperty(workerProp)
        .declareProperty(creditsProp);
  }

  public JobSubscriptionRequest setSubscriberKey(long subscriberKey) {
    this.subscriberKeyProp.setValue(subscriberKey);
    return this;
  }

  public JobSubscriptionRequest setJobType(DirectBuffer jobType) {
    this.jobTypeProp.setValue(jobType);
    return this;
  }

  public JobSubscriptionRequest setTimeout(long timeout) {
    this.timeoutProp.setValue(timeout);
    return this;
  }

  public JobSubscriptionRequest setCredits(int credits) {
    this.creditsProp.setValue(credits);
    return this;
  }

  public JobSubscriptionRequest setWorker(DirectBuffer worker) {
    this.workerProp.setValue(worker);
    return this;
  }

  public long getSubscriberKey() {
    return subscriberKeyProp.getValue();
  }

  public DirectBuffer getJobType() {
    return jobTypeProp.getValue();
  }

  public long getTimeout() {
    return timeoutProp.getValue();
  }

  public int getCredits() {
    return creditsProp.getValue();
  }

  public DirectBuffer getWorker() {
    return workerProp.getValue();
  }
}
