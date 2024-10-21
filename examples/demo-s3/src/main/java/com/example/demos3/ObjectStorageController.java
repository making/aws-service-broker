package com.example.demos3;

import io.awspring.cloud.s3.S3Template;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObjectStorageController {

	private final S3Template s3Template;

	private final S3Props props;

	public ObjectStorageController(S3Template s3Template, S3Props props) {
		this.s3Template = s3Template;
		this.props = props;
	}

	@PostMapping(path = "/persons")
	public void store(@RequestBody Person person) {
		this.s3Template.store(this.props.bucket(), person.id() + ".json", person);
	}

	@GetMapping(path = "/persons/{id}")
	public Person read(@PathVariable Long id) {
		return this.s3Template.read(this.props.bucket(), id + ".json", Person.class);
	}

	@DeleteMapping(path = "/persons/{id}")
	public void delete(@PathVariable Long id) {
		this.s3Template.deleteObject(this.props.bucket(), id + ".json");
	}

	record Person(Long id, String firstName, String lastName) {
	}

}
