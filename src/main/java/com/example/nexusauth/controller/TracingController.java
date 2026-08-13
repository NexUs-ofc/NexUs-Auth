package com.example.nexusauth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class TracingController {
    private static final Logger logger = LoggerFactory.getLogger(TracingController.class);

    @GetMapping("/tracing")
    public String dashboard() {
        return "redirect:/tracing/index.html";
    }
}
