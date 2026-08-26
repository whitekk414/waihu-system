from fastapi.testclient import TestClient

from app.main import create_app


def test_health_reports_model_state():
    client = TestClient(create_app(engine_status=lambda: ("loading", None)))

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "modelState": "loading",
        "modelReady": False,
        "model": "SenseVoiceSmall",
        "loadError": None,
    }


def test_health_reports_model_load_failure():
    client = TestClient(create_app(engine_status=lambda: ("failed", "bad model")))

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "modelState": "failed",
        "modelReady": False,
        "model": "SenseVoiceSmall",
        "loadError": "bad model",
    }
