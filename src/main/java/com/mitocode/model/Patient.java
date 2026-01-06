package com.mitocode.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Objects;

/*@Getter
@Setter
@ToString
@EqualsAndHashCode*/

@NoArgsConstructor //constructor vacio
@AllArgsConstructor //constructor con todos los atributos
@Data //getter y setter, toString and equalsand hashcode
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
//@Table(name = "tbl_patient", schema = "sistemas") -> para definir el nombre de la tabla
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer idPatient;

    @Column(nullable = false, length = 70) //name = "nombre",
    private String firstName; //lowerCamelCase //BD snake first_name

    @Column(nullable = false, length = 70)
    private String lastName;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(length = 150)
    private String address;

    @Column(nullable = false, length = 9)
    private String phone;

    @Column(nullable = false, length = 55)
    private String email;
}
