class App {
    public String name, uid;

    public App(String name, String uid) {
        this.name = name;
        this.uid = uid;
    }

    @Override
    public String toString() {
        return name + ":" + uid;
    }
}