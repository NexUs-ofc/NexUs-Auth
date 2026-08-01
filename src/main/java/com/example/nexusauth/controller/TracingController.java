package com.example.nexusauth.controller;

import com.example.nexusauth.dto.AuthDtos;
import com.example.nexusauth.dto.SessionResponse;
import com.example.nexusauth.service.AuthService;
import com.example.nexusauth.service.LogoutService;
import com.example.nexusauth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
