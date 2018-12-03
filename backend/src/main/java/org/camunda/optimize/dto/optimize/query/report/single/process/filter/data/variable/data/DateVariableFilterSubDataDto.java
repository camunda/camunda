package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.variable.data;

import java.time.OffsetDateTime;

public class DateVariableFilterSubDataDto {
    protected OffsetDateTime start;
    protected OffsetDateTime end;

    public OffsetDateTime getStart() {
        return start;
    }

    public void setStart(OffsetDateTime start) {
        this.start = start;
    }

    public OffsetDateTime getEnd() {
        return end;
    }

    public void setEnd(OffsetDateTime end) {
        this.end = end;
    }
}
