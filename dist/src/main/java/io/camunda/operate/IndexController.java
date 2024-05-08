package io.camunda.operate;

import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

  @Autowired private ServletContext context;

  @GetMapping("/index.html")
  public String index() {
    return "redirect:/operate";
  }

  @GetMapping("/operate")
  public String operate(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/operate/");
    return "operate/index";
  }

  @GetMapping("/tasklist")
  public String tasklist(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/tasklist/");
    return "tasklist/index";
  }
}
