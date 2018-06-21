package org.camunda.operate.entities;

/**
 * @author Svetlana Dorokhova.
 */
public abstract class OperateEntity {

  private String id;

  private Integer partitionId;

  private long position;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(Integer partitionId) {
    this.partitionId = partitionId;
  }

  public long getPosition() {
    return position;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OperateEntity that = (OperateEntity) o;

    if (position != that.position)
      return false;
    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    return partitionId != null ? partitionId.equals(that.partitionId) : that.partitionId == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (partitionId != null ? partitionId.hashCode() : 0);
    result = 31 * result + (int) (position ^ (position >>> 32));
    return result;
  }
}
