package io.camunda.jokegen.repository;

import io.camunda.jokegen.model.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

  @Query("SELECT ur.roleName FROM UserRole ur WHERE ur.user.username = :username")
  List<String> findRoleNamesByUsername(@Param("username") String username);
}
