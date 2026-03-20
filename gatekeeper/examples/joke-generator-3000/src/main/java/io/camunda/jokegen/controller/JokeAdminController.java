package io.camunda.jokegen.controller;

import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.jokegen.service.JokeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/jokes/admin")
public final class JokeAdminController {

  private final JokeService jokeService;
  private final CamundaAuthenticationProvider authProvider;

  public JokeAdminController(
      final JokeService jokeService, final CamundaAuthenticationProvider authProvider) {
    this.jokeService = jokeService;
    this.authProvider = authProvider;
  }

  @GetMapping
  public String adminPage(final Model model) {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    if (springAuth == null
        || !springAuth.isAuthenticated()
        || springAuth instanceof AnonymousAuthenticationToken) {
      return "redirect:/login";
    }
    requireAdminRole();
    final var auth = authProvider.getCamundaAuthentication();
    model.addAttribute("jokes", jokeService.getAllJokes());
    model.addAttribute("username", auth.authenticatedUsername());
    return "admin";
  }

  @PostMapping
  public String createJoke(
      @RequestParam("setup") final String setup,
      @RequestParam("punchline") final String punchline,
      @RequestParam("category") final String category) {
    requireAdminRole();
    final var auth = authProvider.getCamundaAuthentication();
    jokeService.createJoke(setup, punchline, category, auth.authenticatedUsername());
    return "redirect:/jokes/admin";
  }

  private void requireAdminRole() {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    if (springAuth == null
        || !springAuth.isAuthenticated()
        || springAuth instanceof AnonymousAuthenticationToken) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    final var auth = authProvider.getCamundaAuthentication();
    if (auth == null || !auth.authenticatedRoleIds().contains("joke-admin")) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires joke-admin role");
    }
  }
}
