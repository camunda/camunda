package io.camunda.jokegen.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class HomeController {

  private final String authMethod;

  public HomeController(
      @Value("${camunda.security.authentication.method}") final String authMethod) {
    this.authMethod = authMethod;
  }

  @GetMapping("/")
  public String home(final Model model) {
    final String loginUrl = "OIDC".equalsIgnoreCase(authMethod) ? "/jokes" : "/login";
    model.addAttribute("loginUrl", loginUrl);
    return "home";
  }

  @GetMapping("/login")
  public String login() {
    return "login";
  }
}
