package org.tkit.onecx.permission.rs.internal.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.jboss.resteasy.reactive.RestResponse.Status.*;
import static org.jboss.resteasy.reactive.RestResponse.Status.BAD_REQUEST;

import java.util.List;

import jakarta.ws.rs.core.HttpHeaders;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.permission.rs.internal.mappers.ExceptionMapper;
import org.tkit.onecx.permission.test.AbstractTest;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.permission.rs.internal.model.*;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestHTTPEndpoint(AssignmentRestController.class)
@WithDBData(value = "data/test-internal.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
class AssignmentRestControllerTest extends AbstractTest {

    @Test
    void createAssignment() {
        // create Assignment
        var requestDTO = new CreateAssignmentRequestDTO();
        requestDTO.setPermissionId("p11");
        requestDTO.setRoleId("r11");

        var uri = given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post()
                .then()
                .statusCode(CREATED.getStatusCode())
                .extract().header(HttpHeaders.LOCATION);

        var dto = given()
                .contentType(APPLICATION_JSON)
                .get(uri)
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .body().as(AssignmentDTO.class);

        assertThat(dto).isNotNull()
                .returns(requestDTO.getRoleId(), from(AssignmentDTO::getRoleId))
                .returns(requestDTO.getPermissionId(), from(AssignmentDTO::getPermissionId));

        // create Role without body
        var exception = given()
                .when()
                .contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(exception.getErrorCode()).isEqualTo(ExceptionMapper.ErrorKeys.CONSTRAINT_VIOLATIONS.name());
        assertThat(exception.getDetail()).isEqualTo("createAssignment.createAssignmentRequestDTO: must not be null");

        // create Role with existing name
        requestDTO = new CreateAssignmentRequestDTO();
        requestDTO.setPermissionId("p13");
        requestDTO.setRoleId("r13");

        exception = given().when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(exception.getErrorCode()).isEqualTo("PERSIST_ENTITY_FAILED");
        assertThat(exception.getDetail()).isEqualTo(
                "could not execute statement [ERROR: duplicate key value violates unique constraint 'uc_assignment_key'  Detail: Key (permission_id, role_id, tenant_id)=(p13, r13, default) already exists.]");

    }

    @Test
    void createAssignmentWrong() {
        // create Assignment
        var requestDTO = new CreateAssignmentRequestDTO();
        requestDTO.setPermissionId("does-not-exists");
        requestDTO.setRoleId("r11");

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post()
                .then()
                .statusCode(NOT_FOUND.getStatusCode());

        requestDTO.setPermissionId("p11");
        requestDTO.setRoleId("does-not-exists");

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post()
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void batchCreateAssignmentsTest() {
        // create Assignment
        var requestDTO = new CreateProductAssignmentRequestDTO();
        requestDTO.setRoleId("r14");
        requestDTO.setProductNames(List.of("test1"));

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/grant")
                .then()
                .statusCode(CREATED.getStatusCode());

        //should return not-found when no productNames are set
        var invalidRequestDTO = new CreateProductAssignmentRequestDTO();
        invalidRequestDTO.setRoleId("r12");
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(invalidRequestDTO)
                .post("/grant")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());

        //should return not-found when role not existing
        invalidRequestDTO.setRoleId("not-existing");
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(invalidRequestDTO)
                .post("/grant")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());

        //should return not-found when no permissions with given productNames exists

        var request = new CreateProductAssignmentRequestDTO();
        request.setRoleId("r12");
        request.setProductNames(List.of("randomProductName"));

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(request)
                .post("/grant")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void grantAssignmentByAppId() {
        // create Assignment
        var requestDTO = new CreateProductAssignmentRequestDTO();
        requestDTO.setRoleId("r11");
        requestDTO.setAppId("app2");

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/grant")
                .then()
                .statusCode(CREATED.getStatusCode());

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/grant")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode());

    }

    @Test
    void grantAssignmentAppIdNotExistsTest() {
        // create Assignment
        var requestDTONotFound = new CreateProductAssignmentRequestDTO();
        requestDTONotFound.setRoleId("r11");
        requestDTONotFound.setAppId("appID_NOT_EXIST");

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTONotFound)
                .post("/grant")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void revokeAssignmentsByOnlyRoleIdTest() {
        var requestDTO = new RevokeAssignmentRequestDTO();
        requestDTO.roleId("r14");
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/revoke")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        //check if assignment is gone
        given()
                .when()
                .get("a12")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());

        //not-exiting role id
        requestDTO.setRoleId("not-existing");
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/revoke")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());
    }

    @Test
    void revokeAssignmentsByRoleIdAndPermissionIdTest() {
        var requestDTO = new RevokeAssignmentRequestDTO();
        requestDTO.roleId("r14");
        requestDTO.permissionId("p13");
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/revoke")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        //check if assignment is gone
        given()
                .when()
                .get("a12")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void revokeAssignmentsByRoleIdAndAppIdsTest() {
        var requestDTO = new RevokeAssignmentRequestDTO();
        requestDTO.roleId("r14");
        requestDTO.setProductNames(List.of("test1"));
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/revoke")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        //check if assignment is gone
        given()
                .when()
                .get("a12")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void revokeAssignmentsByAppIdTest() {
        var requestDTO = new RevokeAssignmentRequestDTO();
        requestDTO.roleId("r14");
        requestDTO.appId("app2");
        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(requestDTO)
                .post("/revoke")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        //check if assignment is gone
        given()
                .when()
                .get("a13")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void getNotFoundAssignment() {
        given()
                .contentType(APPLICATION_JSON)
                .get("does-not-exists")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void searchAssignmentTest() {
        var criteria = new AssignmentSearchCriteriaDTO();

        var data = given()
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(APPLICATION_JSON)
                .extract()
                .as(AssignmentPageResultDTO.class);

        assertThat(data).isNotNull();
        assertThat(data.getTotalElements()).isEqualTo(3);
        assertThat(data.getStream()).isNotNull().hasSize(3);

        criteria.setAppIds(List.of("  "));

        data = given()
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(APPLICATION_JSON)
                .extract()
                .as(AssignmentPageResultDTO.class);

        assertThat(data).isNotNull();
        assertThat(data.getTotalElements()).isEqualTo(3);
        assertThat(data.getStream()).isNotNull().hasSize(3);

        var criteria2 = new AssignmentSearchCriteriaDTO();

        criteria2.setAppIds(List.of("app1"));

        data = given()
                .contentType(APPLICATION_JSON)
                .body(criteria2)
                .post("/search")
                .then().log().all()
                .statusCode(OK.getStatusCode())
                .contentType(APPLICATION_JSON)
                .extract()
                .as(AssignmentPageResultDTO.class);

        assertThat(data).isNotNull();
        assertThat(data.getTotalElements()).isEqualTo(2);
        assertThat(data.getStream()).isNotNull().hasSize(2);

        //get by multiple appIds
        var multipleAppIdsCriteria = new AssignmentSearchCriteriaDTO();
        multipleAppIdsCriteria.appIds(List.of("app1", "app2", ""));

        var multipleAppIdsResult = given()
                .contentType(APPLICATION_JSON)
                .body(multipleAppIdsCriteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(APPLICATION_JSON)
                .extract()
                .as(AssignmentPageResultDTO.class);

        assertThat(multipleAppIdsResult).isNotNull();
        assertThat(multipleAppIdsResult.getTotalElements()).isEqualTo(3);
        assertThat(multipleAppIdsResult.getStream()).isNotNull().hasSize(3);

    }

    @Test
    void deleteAssignmentTest() {

        // delete Assignment
        given()
                .contentType(APPLICATION_JSON)
                .delete("DELETE_1")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        // check Assignment
        given()
                .contentType(APPLICATION_JSON)
                .get("a11")
                .then()
                .statusCode(OK.getStatusCode());

        // check if Assignment does not exist
        given()
                .contentType(APPLICATION_JSON)
                .delete("a11")
                .then()
                .statusCode(NO_CONTENT.getStatusCode());

        // check Assignment
        given()
                .contentType(APPLICATION_JSON)
                .get("a11")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());

    }
}
