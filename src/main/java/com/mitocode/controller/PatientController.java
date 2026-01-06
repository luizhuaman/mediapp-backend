package com.mitocode.controller;

import com.mitocode.model.Patient;
import com.mitocode.service.impl.PatientServiceImpl;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@RestController: esta notacion da inicio al API REST (COMUNICACION HTTP) -> verbos (GET, POST, PUT, DELETE)
//la comunicacion es a traves de un endPoint
//API: Application Programming Interface: interaccion con un cliente o sistema
//API REST FULL: implementacion de los servicios rest basado en la arquitectura REST
@RestController
//@RequestMapping("/patients") //endPoint
@RequestMapping("${patient.controller.path}")
@RequiredArgsConstructor //constructor de service con campos obligatorios
//@AllArgsConstructor
public class PatientController {

    //@Autowired
    private final PatientServiceImpl service; // = new PatientService();

    /*
    //comento el autowired y hago inyeccion de dependencias por constructor
    public PatientController(PatientService service) {
        this.service = service;
    }*/


    @GetMapping
    public ResponseEntity<List<Patient>> findAll(){
        List<Patient> list = service.findAll();

        //Gano: poder controlar el estado de la respuesta HTTP
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public Patient findById(@PathVariable("id") Integer id){
        return service.findById(id);
    }

    @PostMapping
    public Patient save(@RequestBody Patient patient){
        return service.save(patient);
    }

    //mapeamos el id en la clase Patient para que el metodo PUT sepa que tiene que actualizar un registro
    @PutMapping("/{id}")
    public Patient update(@PathVariable("id") Integer id, @RequestBody Patient patient){
        patient.setIdPatient(id);
        return service.update(id ,patient);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Integer id){
        service.delete(id);
    }

    /*
    @GetMapping
    public Patient save(){
        Patient patient = new Patient();
        patient.setIdPatient(0);
        patient.setFirstName("Mito");
        patient.setLastName("Code");
        patient.setDni("12345678");
        return service.validAndSave(patient);
    }
    */

}
