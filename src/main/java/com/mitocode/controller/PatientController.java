package com.mitocode.controller;

import com.mitocode.dto.PatientDTO;
import com.mitocode.model.Patient;
import com.mitocode.service.impl.PatientServiceImpl;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
    public ResponseEntity<List<PatientDTO>> findAll(){
        List<PatientDTO> list = service.findAll().stream()
                .map(e -> new PatientDTO(e.getIdPatient(), e.getFirstName(), e.getLastName(), e.getDni(), e.getAddress(), e.getPhone(), e.getEmail())).toList();

        //Gano: poder controlar el estado de la respuesta HTTP
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> findById(@PathVariable("id") Integer id){
        Patient obj = service.findById(id);

        //Cualquiera de las dos opciones funciona y llega a response ok 200
        return ResponseEntity.ok(obj);
        //return new ResponseEntity<>(obj, HttpStatus.OK);
    }

    //@RequestBody -> es para leer el cuerpo de la peticion/request
    @PostMapping
    public ResponseEntity<Patient> save(@RequestBody Patient patient){

        Patient obj = service.save(patient);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(obj.getIdPatient()).toUri();

        return ResponseEntity.created(location).build();
    }

    //mapeamos el id en la clase Patient para que el metodo PUT sepa que tiene que actualizar un registro
    //como el body no tiene el id, se usa el setIdPatient(id)
    @PutMapping("/{id}")
    public ResponseEntity<Patient> update(@PathVariable("id") Integer id, @RequestBody Patient patient){
        patient.setIdPatient(id);
        Patient obj = service.update(id ,patient);

        return ResponseEntity.ok(obj);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id){

        service.delete(id);
        return ResponseEntity.noContent().build(); // 204 NO CONTENT //404 NOT FOUND : recurso no encontrado
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
