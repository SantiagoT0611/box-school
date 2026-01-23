package com.storres.box_school.model.dto;

import java.time.LocalDate;

import com.storres.box_school.model.shared.Status;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class StudentResponse {

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate registrationDate;
    private LocalDate expirationDate;
    private Status status;
}
