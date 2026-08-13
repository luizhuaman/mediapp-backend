package com.mitocode.controller;

import com.mitocode.dto.PatientDTO;
import com.mitocode.model.Patient;
import com.mitocode.service.impl.PatientServiceImpl;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

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

    @Qualifier("defaultMapper")
    private final ModelMapper modelMapper;

    /*
    //comento el autowired y hago inyeccion de dependencias por constructor
    public PatientController(PatientService service) {
        this.service = service;
    }*/


    @GetMapping
    public ResponseEntity<List<PatientDTO>> findAll(){
        List<PatientDTO> list = service.findAll().stream()
                .map(this::convertToDTO).toList();

        //Gano: poder controlar el estado de la respuesta HTTP
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientDTO> findById(@PathVariable("id") Integer id){
        Patient obj = service.findById(id);

        //Cualquiera de las dos opciones funciona y llega a response ok 200
        return ResponseEntity.ok(convertToDTO(obj));
        //return new ResponseEntity<>(obj, HttpStatus.OK);
    }

    //@RequestBody -> es para leer el cuerpo de la peticion/request
    @PostMapping
    public ResponseEntity<Void> save(@Valid @RequestBody PatientDTO dto){

        Patient obj = service.save(convertToEntity(dto));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(obj.getIdPatient()).toUri();

        return ResponseEntity.created(location).build();
    }

    //mapeamos el id en la clase Patient para que el metodo PUT sepa que tiene que actualizar un registro
    //como el body no tiene el id, se usa el setIdPatient(id)
    @PutMapping("/{id}")
    public ResponseEntity<PatientDTO> update(@Valid @PathVariable("id") Integer id, @RequestBody PatientDTO dto){
        dto.setIdPatient(id);
        Patient obj = service.update(id ,convertToEntity(dto));

        return ResponseEntity.ok(convertToDTO(obj));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id){

        service.delete(id);
        return ResponseEntity.noContent().build(); // 204 NO CONTENT //404 NOT FOUND : recurso no encontrado
    }

    @GetMapping("/hateoas/{id}")
    public EntityModel<PatientDTO> findByIdHateoas(@PathVariable("id") Integer id){
        EntityModel<PatientDTO> resource = EntityModel.of(convertToDTO(service.findById(id)));
        WebMvcLinkBuilder link1 = linkTo(methodOn(this.getClass()).findById(id));
        WebMvcLinkBuilder link2 = linkTo(methodOn(this.getClass()).findAll());
        resource.add(link1.withRel("patient-info-byId"));
        resource.add(link2.withRel("patient-all-info"));

        return resource;
    }

    private PatientDTO convertToDTO(Patient obj){
        return modelMapper.map(obj, PatientDTO.class);
    }

    private Patient convertToEntity(PatientDTO dto){
        return modelMapper.map(dto, Patient.class);
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
