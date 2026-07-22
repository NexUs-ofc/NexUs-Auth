package com.example.nexusauth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class OtpMailService {
    private final JavaMailSender mailSender;
    private final String from;

    public OtpMailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String email, String otp, String purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Código de confirmação");
        message.setText("Seu código para " + purpose + " é " + otp + ". Ele expira em poucos minutos.");
        mailSender.send(message);
    }
}
