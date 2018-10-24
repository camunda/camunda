# Timer Events

Timer events are events which are triggered by a defined timer. 

## Timer Intermediate Catch Events

![workflow](/bpmn-workflows/timer-intermediate-catch-event.png)

A timer intermediate event acts as a stopwatch. When a token arrives at the timer intermediate catch event then a timer is started. The timer fires after the specified duration is over and the event is left.

The duration must be defined in the [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601#Durations) format. For example:

* `PT15S` - 15 seconds
* `PT1H30M` - 1 hour and 30 minutes
* `P14D` - 14 days

XML representation:

```xml
<bpmn:intermediateCatchEvent id="wait-for-coffee" name="4 minutes">
  <bpmn:timerEventDefinition>
    <bpmn:timeDuration>PT4M</bpmn:timeDuration>
  </bpmn:timerEventDefinition>
</bpmn:intermediateCatchEvent>
```
