package io.camunda.jokegen.controller;

import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.jokegen.model.Joke;
import io.camunda.jokegen.service.JokeService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jokes")
public final class JokeApiController {

  private final JokeService jokeService;
  private final CamundaAuthenticationProvider authProvider;

  public JokeApiController(
      final JokeService jokeService, final CamundaAuthenticationProvider authProvider) {
    this.jokeService = jokeService;
    this.authProvider = authProvider;
  }

  @GetMapping("/random")
  public ResponseEntity<Map<String, Object>> randomJoke() {
    final Joke joke = jokeService.getRandomJoke();
    if (joke == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(
        Map.of(
            "id", joke.getId(),
            "setup", joke.getSetup(),
            "punchline", joke.getPunchline(),
            "category", joke.getCategory()));
  }

  @PostMapping("/generate")
  public ResponseEntity<?> generateJoke(@RequestBody final Map<String, String> body) {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    if (springAuth == null
        || !springAuth.isAuthenticated()
        || springAuth instanceof AnonymousAuthenticationToken) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "Not authenticated"));
    }
    final var auth = authProvider.getCamundaAuthentication();
    if (auth == null || !auth.authenticatedRoleIds().contains("joke-admin")) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(Map.of("error", "Requires joke-admin role"));
    }
    final String setup = body.get("setup");
    final String punchline = body.get("punchline");
    final String category = body.getOrDefault("category", "general");
    final Joke joke = jokeService.createJoke(setup, punchline, category, auth.authenticatedUsername());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            Map.of(
                "id", joke.getId(),
                "setup", joke.getSetup(),
                "punchline", joke.getPunchline(),
                "category", joke.getCategory()));
  }
}
