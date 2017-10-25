package org.camunda.optimize.dto.engine;

public class GroupDto {

  protected String id;
  protected String name;

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object object) {
    if( object instanceof GroupDto) {
      GroupDto anotherGroupDto = (GroupDto) object;
      return this.id.equals(anotherGroupDto.id);
    }
    return false;
  }
}
