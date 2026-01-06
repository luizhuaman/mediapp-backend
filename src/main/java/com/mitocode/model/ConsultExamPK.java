package com.mitocode.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Embeddable
@EqualsAndHashCode
public class ConsultExamPK implements Serializable {
    //Serializable - cuando paso de A -> B a traves de la red

    @ManyToOne
    @JoinColumn(name = "id_consult")
    private Consult consult;

    @ManyToOne
    @JoinColumn(name = "id_exam")
    private Exam exam;
}
