package io.camunda.jokegen.repository;

import io.camunda.jokegen.model.Joke;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JokeRepository extends JpaRepository<Joke, Long> {

  @Query(value = "SELECT * FROM jokes ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
  Joke findRandom();
}
