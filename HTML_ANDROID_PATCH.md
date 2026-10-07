# Bản vá HTML cho AndroidBridge

Trong `exportJsonData()`, sau khi tạo `text` và `name`, thêm trước cơ chế Share/Blob:

```javascript
if (window.Android && typeof window.Android.saveJson === 'function') {
  Android.saveJson(name, text);
  return;
}
```

Để Android Back đóng đúng lớp giao diện, thêm trước `</script>`:

```javascript
window.handleAndroidBack = function () {
  const dialog = document.getElementById('appDialog');
  if (dialog && dialog.classList.contains('show')) {
    closeAppDialog(false);
    return 'handled';
  }
  const form = document.getElementById('modal');
  if (form && form.classList.contains('show')) {
    closeForm();
    return 'handled';
  }
  const language = document.getElementById('languageModal');
  if (language && language.classList.contains('show')) {
    closeLanguages();
    return 'handled';
  }
  const manager = document.getElementById('categoryManager');
  if (manager && manager.classList.contains('show')) {
    closeCategoryManager();
    return 'handled';
  }
  const active = document.querySelector('.screen.active');
  if (active && active.id !== 'home') {
    nav('home');
    return 'handled';
  }
  return 'exit';
};
```
