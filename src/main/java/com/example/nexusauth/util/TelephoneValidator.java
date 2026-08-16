package com.example.nexusauth.util;

import com.example.nexusauth.annotations.TelephoneList;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class TelephoneValidator implements ConstraintValidator<TelephoneList, List<String>> {

    private static final String REGEX = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$";

    @Override
    public boolean isValid(List<String> telephones, ConstraintValidatorContext context){

        if (telephones == null){
            return true;
        }

        for (String number : telephones){
            if (number == null || !number.matches(REGEX)){

                context.disableDefaultConstraintViolation();

                context.buildConstraintViolationWithTemplate(
                        "Telefone inválido: "+number
                ).addConstraintViolation();

                return false;
            }
        }

        return true;
    }
}
