package com.mitocode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotNull
    @NotEmpty
    @NotBlank
    @Size(min = 3, max = 70)
    private String primaryName;

    @NotNull
    @Size(min = 3, max = 70)
    private String surname;

    @NotNull
    @Size(min = 3, max = 12)
    private String cmpMedic;

    @NotNull
    @Size(min = 3, max = 170)
    private String photo;
}
