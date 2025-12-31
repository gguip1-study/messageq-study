package gguip1.study.messagequeuestudy.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HoleController {
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
