package com.mitocode.repo;

import com.mitocode.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

//entre interfases se heredan, solo una clase puede implementar
// El JpaRepository tiene implementado el CRUD y sus atributos son -> <Clase, ID>
public interface IPatientRepo extends JpaRepository<Patient, Integer> {

    //Patient save(Patient patient);
}
