package com.example.nexusauth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TracingController {
    private static final Logger logger = LoggerFactory.getLogger(TracingController.class);

    @GetMapping("/tracing")
    public String dashboard() {
        return "redirect:/tracing/index.html";
    }
}
