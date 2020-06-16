package io.zeebe.tasklist.webapp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ForwardErrorController implements ErrorController  {
 
    private static final Logger logger = LoggerFactory.getLogger(ForwardErrorController.class);
  
    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
      logger.warn("Requested non existing path. Forward (on serverside) to /");
      ModelAndView modelAndView = new ModelAndView("forward:/");
      // Is it really necessary to set status to OK (for frontend)?  
      modelAndView.setStatus(HttpStatus.OK); 
      return modelAndView;
    }
 
    @Override
    public String getErrorPath() {
        return "/error";
    }
}
