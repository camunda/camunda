package io.camunda.jokegen.service;

import io.camunda.jokegen.model.Joke;
import io.camunda.jokegen.repository.JokeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JokeService {

  private final JokeRepository jokeRepository;

  public JokeService(final JokeRepository jokeRepository) {
    this.jokeRepository = jokeRepository;
  }

  @Transactional(readOnly = true)
  public List<Joke> getAllJokes() {
    return jokeRepository.findAll();
  }

  @Transactional(readOnly = true)
  public Joke getRandomJoke() {
    return jokeRepository.findRandom();
  }

  @Transactional
  public Joke createJoke(
      final String setup,
      final String punchline,
      final String category,
      final String createdBy) {
    final Joke joke = new Joke(setup, punchline, category, createdBy);
    return jokeRepository.save(joke);
  }
}
