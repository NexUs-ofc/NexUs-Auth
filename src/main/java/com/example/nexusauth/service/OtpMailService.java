package com.example.nexusauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class OtpMailService {

    private static final Logger logger =
            LoggerFactory.getLogger(OtpMailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public OtpMailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(
            String email,
            String otp,
            String purpose
    ) {

        logger.info(
                "Iniciando envio de email OTP email={} purpose={}",
                email,
                purpose
        );

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(email);
        message.setSubject(
                "Código de confirmação"
        );
        message.setText(
                "Seu código para " +
                        purpose +
                        " é " +
                        otp +
                        ". Ele expira em poucos minutos."
        );

        logger.debug(
                "Mensagem de email OTP preparada email={} remetente={} assunto={}",
                email,
                from,
                message.getSubject()
        );

        mailSender.send(
                message
        );

        logger.info(
                "Email OTP enviado com sucesso email={} purpose={}",
                email,
                purpose
        );
    }
}