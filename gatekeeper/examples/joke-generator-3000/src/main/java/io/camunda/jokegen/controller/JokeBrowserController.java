package io.camunda.jokegen.controller;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.CamundaUserProvider;
import io.camunda.jokegen.service.JokeService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class JokeBrowserController {

  private final JokeService jokeService;
  private final CamundaAuthenticationProvider authProvider;
  private final CamundaUserProvider userProvider;

  public JokeBrowserController(
      final JokeService jokeService,
      final CamundaAuthenticationProvider authProvider,
      final CamundaUserProvider userProvider) {
    this.jokeService = jokeService;
    this.authProvider = authProvider;
    this.userProvider = userProvider;
  }

  @GetMapping("/jokes")
  public String jokes(final Model model) {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    if (springAuth == null
        || !springAuth.isAuthenticated()
        || springAuth instanceof AnonymousAuthenticationToken) {
      return "redirect:/login";
    }

    final CamundaAuthentication auth = authProvider.getCamundaAuthentication();
    if (auth == null) {
      return "redirect:/login";
    }

    final String username = auth.authenticatedUsername();
    final boolean isAdmin = auth.authenticatedRoleIds().contains("joke-admin");

    model.addAttribute("jokes", jokeService.getAllJokes());
    model.addAttribute("username", username);
    model.addAttribute("isAdmin", isAdmin);
    model.addAttribute("userInfo", userProvider.getCurrentUser());
    return "jokes";
  }
}
