package com.mitocode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicDTO {

    //Solo idMedic usa el criterio de equals y hashcode
    @EqualsAndHashCode.Include
    private Integer idMedic;

    private String primaryName;
    private String surname;
    private String cmpMedic;
    private String photo;
}
